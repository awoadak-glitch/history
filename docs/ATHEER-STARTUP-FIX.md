# Atheer startup crash — Firebase registration isolation

User report: the first signed candidate `e2236646…` closes immediately on launch. No device crash log is available, so the exact phone exception has not been observed.

A concrete startup defect was found in our merge: the host ComponentDiscoveryService metadata was unioned with AWR metadata. The resulting host manifest registers both `com.google.firebase.FirebaseCommonKtxRegistrar` and `com.google.firebase.ktx.FirebaseCommonKtxRegistrar`. Both classes exist in the original Oscar DEX and both provide the same four qualified coroutine-dispatcher components. Host FirebaseInitProvider initializes Firebase before Application.onCreate; its CycleDetector throws IllegalArgumentException for duplicate non-set component providers. This defect is sufficient to fail startup independently of signature verification.

The fix leaves the host discovery metadata exactly as it was in Oscar. The embedded module receives a private metadata-only SourceDiscoveryService containing exactly its original registrars. Only the embedded FirebaseApp discovery class reference is changed to use that service. Native libraries, package identity, certificates reported to the platform, IronFingerprint and host FirebaseApp remain unchanged. The retained AWR signing key is reused, allowing this revision to update the previous Atheer candidate without clearing its data.

Regression gates compare each compiled metadata service against its own original manifest, and independently check the DEX reference, original host initializer, and embedded activity adapters. The previous merged manifest must fail this gate. Device startup, subsequent integrity acceptance, catalogue and playback still require phone verification.
