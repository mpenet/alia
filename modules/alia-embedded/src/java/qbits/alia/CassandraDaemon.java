/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package qbits.alia;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.batchlog.LegacyBatchlogMigrator;
import org.apache.cassandra.concurrent.ScheduledExecutors;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.config.SchemaConstants;
import org.apache.cassandra.cql3.functions.ThreadAwareSecurityManager;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.commitlog.CommitLog;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.StartupException;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.hints.LegacyHintsMigrator;
import org.apache.cassandra.io.FSError;
import org.apache.cassandra.io.sstable.CorruptSSTableException;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.schema.LegacySchemaMigrator;
import org.apache.cassandra.thrift.ThriftServer;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.utils.*;

import org.apache.cassandra.service.NativeTransportService;
import org.apache.cassandra.service.StartupChecks;
import org.apache.cassandra.service.DefaultFSErrorHandler;
import org.apache.cassandra.service.CacheService;
import org.apache.cassandra.service.GCInspector;

/**
 * The <code>CassandraDaemon</code> is an abstraction for a Cassandra daemon
 * service, which defines not only a way to activate and deactivate it, but also
 * hooks into its lifecycle methods (see {@link #setup()}, {@link #start()},
 * {@link #stop()} and {@link #setup()}).
 */
public class CassandraDaemon
{
    private static final Logger logger;
    static
    {
        logger = LoggerFactory.getLogger(CassandraDaemon.class);
    }

    static final CassandraDaemon instance = new CassandraDaemon();

    public ThriftServer thriftServer;
    private NativeTransportService nativeTransportService;

    private final boolean runManaged;
    protected final StartupChecks startupChecks;
    private boolean setupCompleted;

    public CassandraDaemon()
    {
        this(false);
    }

    public CassandraDaemon(boolean runManaged)
    {
        this.runManaged = runManaged;
        this.startupChecks = new StartupChecks().withDefaultTests();
        this.setupCompleted = false;
    }

    /**
     * This is a hook for concrete daemons to initialize themselves suitably.
     *
     * Subclasses should override this to finish the job (listening on ports, etc.)
     */
    protected void setup()
    {
        FileUtils.setFSErrorHandler(new DefaultFSErrorHandler());
        ThreadAwareSecurityManager.install();

        logSystemInfo();

        try
        {
            startupChecks.verify();
        }
        catch (StartupException e)
        {
            exitOrFail(e.returnCode, e.getMessage(), e.getCause());
        }

        try
        {
            if (SystemKeyspace.snapshotOnVersionChange())
            {
                SystemKeyspace.migrateDataDirs();
            }
        }
        catch (IOException e)
        {
            exitOrFail(3, e.getMessage(), e.getCause());
        }

        // We need to persist this as soon as possible after startup checks.
        // This should be the first write to SystemKeyspace (CASSANDRA-11742)
        SystemKeyspace.persistLocalMetadata();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            public void uncaughtException(Thread t, Throwable e)
            {
                logger.error("Exception in thread " + t, e);
                Tracing.trace("Exception in thread {}", t, e);
                for (Throwable e2 = e; e2 != null; e2 = e2.getCause())
                {
                    JVMStabilityInspector.inspectThrowable(e2);

                    if (e2 instanceof FSError)
                    {
                        if (e2 != e) // make sure FSError gets logged exactly once.
                            logger.error("Exception in thread " + t, e2);
                        FileUtils.handleFSError((FSError) e2);
                    }

                    if (e2 instanceof CorruptSSTableException)
                    {
                        if (e2 != e)
                            logger.error("Exception in thread " + t, e2);
                        FileUtils.handleCorruptSSTable((CorruptSSTableException) e2);
                    }
                }
            }
        });

        /*
         * Migrate pre-3.0 keyspaces, tables, types, functions, and aggregates, to their new 3.0 storage.
         * We don't (and can't) wait for commit log replay here, but we don't need to - all schema changes force
         * explicit memtable flushes.
         */
        LegacySchemaMigrator.migrate();

        // Populate token metadata before flushing, for token-aware sstable partitioning (#6696)
        StorageService.instance.populateTokenMetadata();

        // load schema from disk
        Schema.instance.loadFromDisk();

        // clean up debris in the rest of the keyspaces
        for (String keyspaceName : Schema.instance.getKeyspaces())
        {
            // Skip system as we've already cleaned it
            if (keyspaceName.equals(SchemaConstants.SYSTEM_KEYSPACE_NAME))
                continue;

            for (CFMetaData cfm : Schema.instance.getTablesAndViews(keyspaceName))
            {
                try
                {
                    ColumnFamilyStore.scrubDataDirectories(cfm);
                }
                catch (StartupException e)
                {
                    exitOrFail(e.returnCode, e.getMessage(), e.getCause());
                }
            }
        }

        Keyspace.setInitialized();

        // initialize keyspaces
        for (String keyspaceName : Schema.instance.getKeyspaces())
        {
            if (logger.isDebugEnabled())
                logger.debug("opening keyspace {}", keyspaceName);
            // disable auto compaction until gossip settles since disk boundaries may be affected by ring layout
            for (ColumnFamilyStore cfs : Keyspace.open(keyspaceName).getColumnFamilyStores())
            {
                for (ColumnFamilyStore store : cfs.concatWithIndexes())
                {
                    store.disableAutoCompaction();
                }
            }
        }

        try
        {
            loadRowAndKeyCacheAsync().get();
        }
        catch (Throwable t)
        {
            JVMStabilityInspector.inspectThrowable(t);
            logger.warn("Error loading key or row cache", t);
        }

        try
        {
            GCInspector.register();
        }
        catch (Throwable t)
        {
            JVMStabilityInspector.inspectThrowable(t);
            logger.warn("Unable to start GCInspector (currently only supported on the Sun JVM)");
        }

        // Replay any CommitLogSegments found on disk
        try
        {
            CommitLog.instance.recoverSegmentsOnDisk();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        // Re-populate token metadata after commit log recover (new peers might be loaded onto system keyspace #10293)
        StorageService.instance.populateTokenMetadata();

        // migrate any legacy (pre-3.0) hints from system.hints table into the new store
        new LegacyHintsMigrator(DatabaseDescriptor.getHintsDirectory(), DatabaseDescriptor.getMaxHintsFileSize()).migrate();

        // migrate any legacy (pre-3.0) batch entries from system.batchlog to system.batches (new table format)
        LegacyBatchlogMigrator.migrate();

        SystemKeyspace.finishStartup();

        // Prepared statements
        QueryProcessor.preloadPreparedStatement();

        // start server internals
        StorageService.instance.registerDaemon(this);
        try
        {
            StorageService.instance.initServer();
        }
        catch (ConfigurationException e)
        {
            System.err.println(e.getMessage() + "\nFatal configuration error; unable to start server.  See log for stacktrace.");
            exitOrFail(1, "Fatal configuration error", e);
        }

        // Because we are writing to the system_distributed keyspace, this should happen after that is created, which
        // happens in StorageService.instance.initServer()
        Runnable viewRebuild = () -> {
            for (Keyspace keyspace : Keyspace.all())
            {
                keyspace.viewManager.buildAllViews();
            }
            logger.debug("Completed submission of build tasks for any materialized views defined at startup");
        };

        ScheduledExecutors.optionalTasks.schedule(viewRebuild, StorageService.RING_DELAY, TimeUnit.MILLISECONDS);

        if (!FBUtilities.getBroadcastAddress().equals(InetAddress.getLoopbackAddress()))
            Gossiper.waitToSettle();

        // re-enable auto-compaction after gossip is settled, so correct disk boundaries are used
        for (Keyspace keyspace : Keyspace.all())
        {
            for (ColumnFamilyStore cfs : keyspace.getColumnFamilyStores())
            {
                for (final ColumnFamilyStore store : cfs.concatWithIndexes())
                {
                    store.reload(); //reload CFs in case there was a change of disk boundaries
                    if (store.getCompactionStrategyManager().shouldBeEnabled())
                    {
                        store.enableAutoCompaction();
                    }
                }
            }
        }

        InetAddress rpcAddr = DatabaseDescriptor.getRpcAddress();
        int rpcPort = DatabaseDescriptor.getRpcPort();
        int listenBacklog = DatabaseDescriptor.getRpcListenBacklog();
        thriftServer = new ThriftServer(rpcAddr, rpcPort, listenBacklog);

        // Native transport
        nativeTransportService = new NativeTransportService();

        completeSetup();
    }

    /*
     * Asynchronously load the row and key cache in one off threads and return a compound future of the result.
     * Error handling is pushed into the cache load since cache loads are allowed to fail and are handled by logging.
     */
    private ListenableFuture<?> loadRowAndKeyCacheAsync()
    {
        final ListenableFuture<Integer> keyCacheLoad = CacheService.instance.keyCache.loadSavedAsync();

        final ListenableFuture<Integer> rowCacheLoad = CacheService.instance.rowCache.loadSavedAsync();

        @SuppressWarnings("unchecked")
        ListenableFuture<List<Integer>> retval = Futures.successfulAsList(keyCacheLoad, rowCacheLoad);

        return retval;
    }

    @VisibleForTesting
    public void completeSetup()
    {
        setupCompleted = true;
    }

    public boolean setupCompleted()
    {
        return setupCompleted;
    }

    private void logSystemInfo()
    {
    }

    /**
     * Initialize the Cassandra Daemon based on the given <a
     * href="http://commons.apache.org/daemon/jsvc.html">Commons
     * Daemon</a>-specific arguments. To clarify, this is a hook for JSVC.
     *
     * @param arguments
     *            the arguments passed in from JSVC
     * @throws IOException
     */
    public void init(String[] arguments) throws IOException
    {
        setup();
    }

    /**
     * Start the Cassandra Daemon, assuming that it has already been
     * initialized via {@link #init(String[])}
     *
     * Hook for JSVC
     */
    public void start()
    {
        String nativeFlag = System.getProperty("cassandra.start_native_transport");
        if ((nativeFlag != null && Boolean.parseBoolean(nativeFlag)) || (nativeFlag == null && DatabaseDescriptor.startNativeTransport()))
        {
            startNativeTransport();
            StorageService.instance.setRpcReady(true);
        }
        else
            logger.info("Not starting native transport as requested. Use JMX (StorageService->startNativeTransport()) or nodetool (enablebinary) to start it");

    }

    /**
     * Stop the daemon, ideally in an idempotent manner.
     *
     * Hook for JSVC / Procrun
     */
    public void stop()
    {
        // On linux, this doesn't entirely shut down Cassandra, just the RPC server.
        // jsvc takes care of taking the rest down
        logger.info("Cassandra shutting down...");
        if (thriftServer != null)
            thriftServer.stop();
        if (nativeTransportService != null)
            nativeTransportService.destroy();
        StorageService.instance.setRpcReady(false);

    }


    /**
     * Clean up all resources obtained during the lifetime of the daemon. This
     * is a hook for JSVC.
     */
    public void destroy()
    {}

    /**
     * A convenience method to initialize and start the daemon in one shot.
     */
    public void activate()
    {
        // Do not put any references to DatabaseDescriptor above the forceStaticInitialization call.
        try
        {
            applyConfig();

            setup();

            String pidFile = System.getProperty("cassandra-pidfile");

            if (pidFile != null)
            {
                new File(pidFile).deleteOnExit();
            }

            start();
        }
        catch (Throwable e)
        {
            boolean logStackTrace =
                    e instanceof ConfigurationException ? ((ConfigurationException)e).logStackTrace : true;

            System.out.println("Exception (" + e.getClass().getName() + ") encountered during startup: " + e.getMessage());

            if (logStackTrace)
            {
                if (runManaged)
                    logger.error("Exception encountered during startup", e);
                // try to warn user on stdout too, if we haven't already detached
                e.printStackTrace();
                exitOrFail(3, "Exception encountered during startup", e);
            }
            else
            {
                if (runManaged)
                    logger.error("Exception encountered during startup: {}", e.getMessage());
                // try to warn user on stdout too, if we haven't already detached
                System.err.println(e.getMessage());
                exitOrFail(3, "Exception encountered during startup: " + e.getMessage());
            }
        }
    }

    public void applyConfig()
    {
        DatabaseDescriptor.daemonInitialization();
    }

    public void startNativeTransport()
    {
        if (nativeTransportService == null)
            throw new IllegalStateException("setup() must be called first for CassandraDaemon");
        else
            nativeTransportService.start();
    }

    public void stopNativeTransport()
    {
        if (nativeTransportService != null)
            nativeTransportService.stop();
    }

    public boolean isNativeTransportRunning()
    {
        return nativeTransportService != null ? nativeTransportService.isRunning() : false;
    }


    /**
     * A convenience method to stop and destroy the daemon in one shot.
     */
    public void deactivate()
    {
        stop();
        destroy();
    }

    public static void stop(String[] args)
    {
        instance.deactivate();
    }

    public static void main(String[] args)
    {
        instance.activate();
    }

    private void exitOrFail(int code, String message)
    {
        exitOrFail(code, message, null);
    }

    private void exitOrFail(int code, String message, Throwable cause)
    {
        RuntimeException t = cause!=null ? new RuntimeException(message, cause) : new RuntimeException(message);
        throw t;
    }

    public interface Server
    {
        /**
         * Start the server.
         * This method shoud be able to restart a server stopped through stop().
         * Should throw a RuntimeException if the server cannot be started
         */
        public void start();

        /**
         * Stop the server.
         * This method should be able to stop server started through start().
         * Should throw a RuntimeException if the server cannot be stopped
         */
        public void stop();

        /**
         * Returns whether the server is currently running.
         */
        public boolean isRunning();
    }
}
