# VocaMind

Android foundation for VocaMind, using Java 17, Material 3 and XML/ViewBinding
with a Clean MVVM architecture baseline. The application opens a Home navigation
shell with placeholder routes; business features are not implemented yet.

Android CI runs local unit tests and builds the debug APK for pull requests
targeting `develop` or `main` and pushes to `develop`, `main` or `feature/*`,
using JDK 17 and the Gradle wrapper.
See [ARC-03 validation](docs/arc-03-validation.md) for verification results and
the remaining GitHub merge-gate setup.

## Documentation

- [DATA-03 DAO foundation plan](docs/plans/data-03-dao-foundation.md)
- [DATA-03 implementation and validation](docs/data-03-validation.md)
- [DATA-02 UserDatabase plan](docs/plans/data-02-user-database.md)
- [DATA-02 implementation and validation](docs/data-02-validation.md)

- [Documentation index](docs/README.md)
- [Android setup and build commands](docs/android-setup.md)
- [Android foundation and dependency baseline](docs/android-foundation.md)
- [ARC-01 implementation plan](docs/plans/arc-01-android-foundation.md)
- [ARC-01 validation results](docs/arc-01-validation.md)
- [ARC-02 navigation shell plan](docs/plans/arc-02-navigation-shell.md)
- [ARC-02 validation results](docs/arc-02-validation.md)
- [ARC-03 CI foundation plan](docs/plans/arc-03-ci-foundation.md)
- [ARC-03 validation results](docs/arc-03-validation.md)
