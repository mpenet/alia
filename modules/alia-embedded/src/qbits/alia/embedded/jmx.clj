(ns qbits.alia.embedded.jmx
  (:import com.sun.jmx.mbeanserver.JmxMBeanServer
           javax.management.ObjectName
           java.lang.management.ManagementFactory))

(defn object-name
  [^String desc]
  (ObjectName. desc))

(defn unregister!
  [desc]
  (.unregisterMBean (ManagementFactory/getPlatformMBeanServer)
                    (object-name desc)))
