(ns qbits.alia.policy.address-translator
  (:import
   (com.datastax.driver.core.policies
    EC2MultiRegionAddressTranslator
    IdentityTranslator)))

(defn ec2-multi-region-address-translator
  "AddressTranslator implementation for a multi-region EC2 deployment where
  clients are also deployed in EC2.

  Its distinctive feature is that it translates addresses according to
  the location of the Cassandra host:

  addresses in different EC2 regions (than the client) are unchanged;
  addresses in the same EC2 region are translated to private IPs.
  This optimizes network costs, because Amazon charges more for
  communication over public IPs."
  []
  (EC2MultiRegionAddressTranslator.))

(defn identity-translator
  "The default AddressTranslator used by the driver that do no translation."
  []
  (IdentityTranslator.))
