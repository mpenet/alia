(ns qbits.alia.policy.address-translater
  (:import
   (com.datastax.driver.core.policies
    EC2MultiRegionAddressTranslater
    IdentityTranslater)))

(defn ec2-multi-region-address-translater
  "AddressTranslater implementation for a multi-region EC2 deployment where
  clients are also deployed in EC2.

  Its distinctive feature is that it translates addresses according to
  the location of the Cassandra host:

  addresses in different EC2 regions (than the client) are unchanged;
  addresses in the same EC2 region are translated to private IPs.
  This optimizes network costs, because Amazon charges more for
  communication over public IPs."
  []
  (EC2MultiRegionAddressTranslater.))

(defn identity-translater
  "The default AddressTranslater used by the driver that do no translation."
  []
  (IdentityTranslater.))
