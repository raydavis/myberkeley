# Account Provider Bundle

These interfaces and classes support institutional provisioning of fully functional new user accounts. The following scenarios are all supported:

* User accounts (including profiles and any institution-specific modifications to the user's home) created from a single POST rather than requiring multiple POSTs.
    [CalCentral example](https://github.com/ets-berkeley-edu/myberkeley/blob/dev/provision/src/main/java/edu/berkeley/myberkeley/provision/CalOaeAuthorizableService.java)

* Server-side provisioning of user account properties from an external system.
    [CalCentral example](https://github.com/ets-berkeley-edu/myberkeley/blob/dev/provision/src/main/java/edu/berkeley/myberkeley/provision/OraclePersonAttributeProvider.java)

* Self-registration after authentication by an external system such as CAS or Shibboleth, with properties supplied from an integration service and with opted-in participant status noted.
    [CalCentral example](https://github.com/ets-berkeley-edu/3akai-ux/tree/dev/devwidgets/selfregister)

* Registration from an administrative form, with properties supplied from an integration service.
    [CalCentral example](https://github.com/ets-berkeley-edu/3akai-ux/tree/dev/devwidgets/accountprovision)
