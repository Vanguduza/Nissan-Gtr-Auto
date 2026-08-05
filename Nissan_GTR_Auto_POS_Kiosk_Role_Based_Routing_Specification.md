# Nissan GTR Auto Branded POS Terminal, Kiosk, Authentication and Role-Based Routing Specification

## 1. Purpose

This specification defines the required functionality for transforming the Android tablet used at the Nissan GTR Auto shop counter into a fully branded, secure, dedicated POS terminal.

The tablet must not behave like a normal consumer Android device during ordinary business use. It must operate as a controlled Nissan GTR Auto business terminal with the following experience:

```text
Tablet Power On
        ↓
Nissan GTR Auto Branded Boot Experience
        ↓
Animated Nissan GT-R Startup Splash Screen
        ↓
Nissan GTR Auto Staff Login Screen
        ↓
Staff Authentication
        ↓
Load Staff Role and Permissions
        ↓
Role-Based Automatic Routing
        ↓
POS Screen or Authorized Staff Dashboard
```

The system must support:

- Nissan GTR Auto branding throughout the startup and login experience.
- An animated Nissan GT-R startup sequence.
- Optional synchronized GT-R engine audio.
- Secure staff authentication.
- Role-based dashboards.
- Automatic routing based on the authenticated staff member's role.
- Direct routing to the POS interface for sales personnel.
- Screen-level, module-level, action-level and data-level permissions.
- Android kiosk functionality.
- Blocking of unauthorized Android applications and system features.
- Controlled administrative bypass access.
- Automatic recovery after device reboot, application failure or power interruption.

The implementation must integrate into the existing application architecture without unnecessarily replacing, deleting or rewriting working functionality.

# 2. Core User Experience

## 2.1 Required Startup Flow

```text
DEVICE POWER BUTTON PRESSED
        ↓
ANDROID DEVICE BOOTS
        ↓
NISSAN GTR AUTO BRANDED BOOT EXPERIENCE
        ↓
ANIMATED NISSAN GT-R STARTUP SCREEN
        ↓
NISSAN GTR AUTO STAFF LOGIN
        ↓
STAFF ENTERS CREDENTIALS
        ↓
AUTHENTICATION SUCCEEDS
        ↓
LOAD STAFF PROFILE
        ↓
LOAD STAFF ROLE
        ↓
LOAD EFFECTIVE PERMISSIONS
        ↓
ROUTE USER TO AUTHORIZED DESTINATION
```

The startup flow must be smooth and should not expose the standard Android launcher, home screen, notification shade or unrelated Android applications.

The application must automatically launch after:

- Device power-on.
- Device restart.
- Recovery from an unexpected shutdown.
- Recovery after a power interruption.
- Application process restart where supported by Android device policy.
- Reboot initiated by an authorized administrator.

# 3. Nissan GTR Auto Branded Boot Experience

## 3.1 Boot Branding Requirement

The tablet must provide a Nissan GTR Auto branded startup experience.

The application-level branded startup experience must be implemented even if the physical tablet manufacturer does not allow replacement of the earliest firmware-level boot logo.

The implementation must distinguish between:

### Firmware-Level Boot Logo

This is the image displayed by the tablet before Android starts.

Changing this image may require:

- OEM support.
- Custom firmware.
- Bootloader modification.
- Device-specific firmware tools.
- Manufacturer authorization.

Do not implement unsafe bootloader modifications, rooting or firmware changes as part of the normal Android application.

### Application-Level Branded Startup

This is the required implementation.

After Android starts, the Nissan GTR Auto application must automatically open a full-screen branded startup experience before displaying the staff login screen.

The application-level experience must:

- Hide the Android status bar.
- Hide Android navigation controls where supported.
- Prevent access to the normal Android launcher.
- Prevent visible transition flashes to the Android home screen.
- Launch in immersive full-screen mode.
- Display Nissan GTR Auto branding.
- Continue automatically to the animated GT-R splash sequence.

# 4. Animated Nissan GT-R Startup Splash Screen

## 4.1 Functional Requirement

After the application-level branded startup is initialized, display a full-screen animated Nissan GT-R startup sequence.

The animation should communicate performance, speed and automotive identity.

The desired sequence is:

```text
0.0 seconds
Black or dark premium background.

0.3 seconds
Subtle GT-R engine idle audio begins.

0.7 seconds
Vehicle headlights or a GT-R silhouette becomes visible.

1.2 seconds
Engine rev rises.

1.7 seconds
The Nissan GT-R accelerates rapidly from the left side of the screen.

2.0–3.2 seconds
The GT-R travels across the display with motion effects.

3.2 seconds
The vehicle exits the display.

3.3 seconds
Nissan GTR Auto branding is revealed.

3.8 seconds
Nissan GTR Auto logo settles into its final position.

4.2 seconds
Display: "INITIALIZING BUSINESS SYSTEM"

4.5–5.5 seconds
Transition smoothly to the staff login screen.
```

The animation duration should be configurable but should normally remain between approximately 4 and 6 seconds.

## 4.2 Animation Requirements

The animation must:

- Run in full-screen immersive mode.
- Use high-quality local animation resources.
- Avoid downloading animation content during startup.
- Be optimized for the target tablet resolution.
- Maintain acceptable startup performance.
- Avoid excessive memory usage.
- Avoid blocking application initialization.
- Scale correctly across supported tablet screen sizes.
- Support landscape orientation.
- Use smooth transitions.
- Avoid visible Android system UI.

The animation may be implemented using:

- Jetpack Compose animation.
- Lottie animation.
- Optimized local video playback.
- Frame animation.
- Custom Canvas animation.
- A combination of animated visual assets and synchronized audio.

Do not introduce a new UI framework if the existing project already uses a stable and appropriate architecture.

## 4.3 GT-R Engine Audio

The startup animation should include a synchronized GT-R-inspired engine startup and acceleration sound.

The audio sequence may include:

- Low engine idle.
- Engine rev.
- Acceleration sound.
- Turbo or intake effect where appropriate.
- Gear-change effect where appropriate.
- Short exhaust note as the vehicle exits the screen.
- Short branded completion sound when the Nissan GTR Auto logo is revealed.

The audio must:

- Be stored locally in the application or approved application resources.
- Start in synchronization with the animation.
- Respect the device media volume.
- Not override system safety volume controls.
- Stop immediately when the animation ends.
- Stop when the application is closed.
- Stop when the device is muted.
- Not continue playing in the background.
- Not interfere with POS notifications or accessibility features.

The application must provide configuration options for startup animation, engine audio and startup audio volume.

The startup animation should normally play once after a full device boot. It should not play repeatedly every time a user logs out unless explicitly enabled by an administrator.

## 4.4 Asset and Licensing Requirements

Do not assume that Nissan trademarks, GT-R imagery, vehicle recordings or engine sounds may be used without authorization.

All visual and audio assets must be:

- Original assets created for Nissan GTR Auto.
- Properly licensed assets.
- Assets supplied by the business with the required usage rights.
- Legally approved for commercial use.

Do not copy protected video, audio or imagery from third-party websites without authorization.

The application architecture should make the startup animation assets replaceable through a controlled asset configuration process.

# 5. Nissan GTR Auto Staff Login

## 5.1 Login Screen

After the startup animation is complete, automatically display the Nissan GTR Auto staff login screen.

The login screen must include:

- Nissan GTR Auto branding.
- Staff login title.
- Employee ID field.
- Secure PIN field.
- Sign-in action.
- Assigned counter identifier.
- System connectivity status.

The final UI should use the existing Nissan GTR Auto design system, typography, colors, icons and component standards.

## 5.2 Supported Authentication Methods

### Primary Method

- Employee ID or staff number.
- Secure numeric PIN.

### Optional Methods

- Username and password.
- NFC employee card.
- QR employee badge.
- Barcode employee card.
- Fingerprint authentication where supported by the tablet.
- Other approved authentication methods.

## 5.3 Login Security

Authentication must include:

- Secure credential validation.
- No plaintext PIN storage.
- Secure password/PIN hashing on the server where applicable.
- Secure local credential handling.
- Android Keystore integration where appropriate.
- Protection against unauthorized local credential extraction.
- Session expiration.
- Automatic session lock after inactivity.
- Failed login attempt tracking.
- Configurable temporary lockout after repeated failed attempts.
- Authentication audit logging.

# 6. Staff Profile, Role and Permission Loading

After authentication succeeds, the application must load the authenticated user's effective access profile.

The profile must include, where applicable:

- Staff ID.
- Staff name.
- Role ID.
- Role name.
- Department.
- Branch.
- Assigned location.
- Assigned terminal.
- Employment status.
- Active/inactive status.
- Default landing screen.
- Module permissions.
- Screen permissions.
- Action permissions.
- Data-scope permissions.
- Approval permissions.
- Session restrictions.

The application must not route the user to a dashboard until the required role and permission information has been loaded or validated.

# 7. Role-Based Automatic Routing

## 7.1 Routing Requirement

The application must automatically route the authenticated user to the correct destination based on role and configured permissions.

The user must not be required to manually select a dashboard after login.

The routing system must be configurable and database-driven where possible.

Do not hard-code all routing decisions directly into the user interface.

## 7.2 Salesperson Direct POS Routing

If the authenticated user has the role `Salesperson`, the application must automatically route the user directly to the POS sales screen.

The salesperson must not first be routed to a generic dashboard.

Required flow:

```text
Salesperson Login
        ↓
Authentication Successful
        ↓
Load Salesperson Permissions
        ↓
Open POS Screen Automatically
        ↓
Prepare New Sale
```

The POS screen should be ready for immediate use.

The user should be able to perform authorized actions including:

- Search for products.
- Search for parts.
- Search by OEM part number.
- Search by internal stock code.
- Scan a barcode.
- Search by vehicle information where supported.
- Add products to the current sale.
- Select or create a customer.
- View stock availability.
- View authorized product information.
- Create quotations.
- Hold transactions.
- Resume authorized held transactions.
- Process authorized payments.
- Print receipts.
- Complete sales.

## 7.3 Example Role Routing

| Role | Default Destination |
|---|---|
| Salesperson | POS Sales Screen |
| Senior Salesperson | Enhanced POS Screen |
| Cashier | Cashier or Payment Dashboard |
| Storekeeper | Inventory Dashboard |
| Parts Specialist | Parts Catalogue Dashboard |
| Workshop Staff | Workshop or Job Card Dashboard |
| Service Advisor | Service Dashboard |
| Supervisor | Operations Dashboard |
| Manager | Management Dashboard |
| Administrator | Administration Dashboard |
| System Technician | Device Maintenance Console |

The role names and destinations must be configurable.

# 8. Role-Based Dashboard Requirements

Each role-based dashboard must:

- Display only authorized modules.
- Display only authorized navigation items.
- Display only authorized data.
- Use the staff member's assigned branch or location.
- Respect the staff member's data scope.
- Display relevant operational information.
- Avoid showing unnecessary modules.
- Maintain a consistent Nissan GTR Auto visual identity.

## 8.1 Salesperson Interface

The salesperson should land directly on the POS screen.

The POS screen may include:

- Product, part, OEM number, stock code or vehicle search.
- Barcode scanning.
- Current sale.
- Hold sale.
- Checkout.
- Total amount.
- Customer selection.
- Receipt printing.

The salesperson should not automatically have access to:

- Company-wide profit reports.
- Payroll.
- Staff administration.
- System configuration.
- Android settings.
- Device policy settings.
- Unrestricted inventory adjustments.
- Unrestricted price changes.
- Unauthorized transaction deletion.

## 8.2 Storekeeper Dashboard

The storekeeper dashboard may include:

- Stock receiving.
- Stock transfers.
- Stock counts.
- Stock adjustments where authorized.
- Reorder alerts.
- Stock location management.
- Inventory movement history.
- Inventory discrepancy reporting.

## 8.3 Manager Dashboard

The manager dashboard may include:

- Sales performance.
- Inventory performance.
- Staff performance.
- Branch performance.
- Approval requests.
- Discount approvals.
- Refund approvals.
- Operational reports.
- Financial summaries where authorized.

Manager access must still be permission-controlled.

# 9. Permission Architecture

## 9.1 Permission Layers

```text
ROLE
    ↓
MODULE ACCESS
    ↓
SCREEN ACCESS
    ↓
ACTION ACCESS
    ↓
DATA-SCOPE ACCESS
    ↓
APPROVAL AUTHORITY
```

The system must not rely only on hiding buttons.

Every protected action must be validated by the application's business logic and, where applicable, by the backend.

## 9.2 Module-Level Permissions

Examples:

- POS.
- Inventory.
- Parts catalogue.
- Customers.
- Suppliers.
- Purchasing.
- Workshop.
- Reports.
- Finance.
- Staff management.
- System administration.
- Device administration.

## 9.3 Screen-Level Permissions

Examples:

- POS main screen.
- POS transaction history.
- Inventory dashboard.
- Stock receiving screen.
- Stock adjustment screen.
- Manager dashboard.
- Financial reports.
- Staff management screen.
- Device administration screen.

## 9.4 Action-Level Permissions

Examples:

- Create sale.
- Edit sale.
- Cancel sale.
- Hold sale.
- Resume sale.
- Apply discount.
- Override price.
- Process refund.
- Void transaction.
- Create customer.
- Edit customer.
- Receive stock.
- Transfer stock.
- Adjust stock.
- Approve adjustment.
- View reports.
- Export reports.
- Manage staff.
- Change user roles.
- Access device settings.

## 9.5 Data-Scope Permissions

Permissions must support restrictions such as:

- Own transactions only.
- Assigned counter only.
- Assigned branch only.
- Assigned department only.
- All branches.
- Company-wide access.

# 10. Permission Enforcement

The system must enforce permissions in:

1. Navigation visibility.
2. Screen access.
3. User interface controls.
4. ViewModel or presentation logic.
5. Domain/business logic.
6. Local database queries where applicable.
7. Backend API authorization.
8. Report generation.
9. Data export.
10. Administrative actions.

Hiding a button is not sufficient. The application must also reject the action if an unauthorized user attempts to invoke it through another application path or API request.

Required conceptual flow:

```text
USER REQUESTS ACTION
        ↓
CHECK ACTIVE SESSION
        ↓
CHECK USER STATUS
        ↓
CHECK ROLE
        ↓
CHECK EFFECTIVE PERMISSION
        ↓
CHECK DATA SCOPE
        ↓
CHECK APPROVAL REQUIREMENT
        ↓
ALLOW OR DENY ACTION
```

# 11. Configurable Default Landing Screens

Each role must have a configurable default landing destination.

Example:

```text
Role: Salesperson
Default Landing Screen: POS_MAIN

Permissions:
POS_VIEW
POS_CREATE_SALE
POS_ADD_ITEM
POS_HOLD_SALE
POS_CHECKOUT
POS_PRINT_RECEIPT
```

The default landing screen should be stored in the role configuration or role-to-dashboard mapping.

Do not permanently hard-code all landing destinations in the navigation layer.

# 12. Android Dedicated Device and Kiosk Requirements

## 12.1 Normal User Restrictions

During normal operation, staff must not be able to access:

- Standard Android home screen.
- Android application launcher.
- Android recent applications screen.
- Android notification shade.
- Android quick settings.
- Android Settings.
- Google Play Store.
- Web browser.
- Unauthorized applications.
- Unapproved system applications.
- Developer options.
- USB file browsing.
- Unauthorized application installation.
- Unapproved device configuration.

The device must remain focused on the Nissan GTR Auto application.

## 12.2 Recommended Android Implementation

Use supported Android enterprise and kiosk technologies where compatible with the target device:

- Android Enterprise.
- Device Owner mode.
- Lock Task Mode.
- DevicePolicyManager.
- Dedicated-device configuration.
- Application allow-listing.
- Managed application policies.
- Custom launcher or kiosk launcher functionality.
- Controlled startup behavior.

Do not depend only on immersive mode because immersive mode alone does not provide sufficient kiosk security.

## 12.3 Application Recovery

The device must return to the Nissan GTR Auto application after:

- Device reboot.
- Application restart.
- Accidental exit attempt.
- Unauthorized Home navigation attempt.
- Unauthorized Recent Apps navigation attempt where the device policy supports blocking it.

# 13. Authorized Android and Device Bypass

## 13.1 Maintenance Mode

Authorized personnel must be able to access approved device functions through a secure maintenance mode.

The normal staff user must not have unrestricted Android access.

Required flow:

```text
NISSAN GTR AUTO APPLICATION
        ↓
AUTHORIZED MAINTENANCE ACCESS
        ↓
ENTER ADMINISTRATOR CREDENTIALS
        ↓
VERIFY PERMISSIONS
        ↓
OPEN DEVICE ADMINISTRATION CONSOLE
        ↓
ACCESS APPROVED DEVICE FUNCTIONS
```

## 13.2 Device Administration Console

The application should provide a branded Nissan GTR Auto Device Administration Console.

Possible functions include:

- Network configuration.
- Wi-Fi configuration.
- Bluetooth configuration.
- Printer configuration.
- Barcode scanner configuration.
- POS synchronization.
- Device diagnostics.
- Application logs.
- Software updates.
- Device information.
- Date and time configuration where authorized.
- Restart device.
- Shut down device.
- Exit maintenance mode.

## 13.3 Full Android Settings Access

Full Android Settings access should be treated as a higher-level permission.

It must not be automatically granted to every manager or supervisor.

Full access should require:

- Authorized system administrator role.
- Secure authentication.
- Explicit permission.
- Optional second authentication factor.
- Audit logging.

The application should prefer controlled device settings screens over unrestricted Android Settings access.

## 13.4 Maintenance Session Security

Maintenance mode must:

- Record who entered maintenance mode.
- Record the terminal used.
- Record the time.
- Record the actions performed where practical.
- Automatically expire after configurable inactivity.
- Return the device to kiosk mode after expiration.
- Require reauthentication after expiration.
- Prevent ordinary staff from remaining in maintenance mode.

# 14. Session Management

## 14.1 Staff Session

After successful login, create a secure staff session containing:

- Session ID.
- Staff ID.
- Role ID.
- Effective permissions.
- Assigned branch.
- Assigned terminal.
- Login time.
- Last activity time.
- Session status.

## 14.2 Automatic Lock

The terminal should automatically lock after configurable inactivity.

Example:

```text
No activity for 5 minutes
        ↓
Current screen is protected
        ↓
Nissan GTR Auto lock screen appears
        ↓
Staff member enters PIN
        ↓
Authorized session resumes
```

## 14.3 Logout

When the user logs out:

- End or invalidate the active session.
- Clear sensitive in-memory data.
- Clear temporary authentication state.
- Return to the Nissan GTR Auto staff login screen.
- Do not return to the Android launcher.
- Do not replay the full GT-R startup animation by default.

# 15. Offline Operation

The role and permission system must work safely when the device is offline.

Requirements:

- Cache the last valid authorized staff role where permitted.
- Cache the effective permissions securely.
- Store offline data using encrypted local storage where appropriate.
- Record offline transactions for later synchronization.
- Prevent unauthorized role escalation while offline.
- Apply the last valid permission policy until synchronization is restored.
- Synchronize permission changes when connectivity returns.
- Handle revoked users safely.

# 16. Error Handling

## Authentication Failure

If login fails:

- Display a clear but non-sensitive error.
- Do not reveal unnecessary account information.
- Allow the user to retry.
- Track failed attempts.
- Apply lockout policy where configured.

## Role Configuration Failure

If authentication succeeds but no valid role or landing screen is configured, display an appropriate error and do not route the user to a generic unrestricted dashboard.

## Permission Loading Failure

If permissions cannot be loaded:

- Attempt secure local cached permission retrieval where permitted.
- If no valid permission set is available, deny access.
- Do not grant default administrator access.
- Do not route to an unrestricted screen.

## Unauthorized Access

If a user attempts to open an unauthorized screen or action, display an access-denied message and log the event where appropriate.

# 17. Architecture Integration Requirements

Integrate this functionality into the existing architecture.

Before implementing:

1. Inspect the existing project structure.
2. Identify the current UI framework.
3. Identify the current navigation architecture.
4. Identify the current authentication implementation.
5. Identify the current user, role and permission models.
6. Identify the existing local database.
7. Identify the existing backend API architecture.
8. Identify the existing session management implementation.
9. Identify the existing POS module.
10. Identify the existing dashboard modules.
11. Identify existing kiosk, device policy or startup logic.

Do not replace existing working architecture without a technical reason.

Maintain:

- Existing package structure.
- Existing naming conventions.
- Existing dependency injection patterns.
- Existing repository patterns.
- Existing domain models.
- Existing navigation patterns.
- Existing database patterns.
- Existing backend integration patterns.
- Existing error handling conventions.
- Existing UI design system.

# 18. Suggested Feature Structure

Adapt the following structure to the existing project rather than forcing an unnecessary new architecture:

```text
feature/
├── startup/
│   ├── StartupActivity
│   ├── StartupViewModel
│   ├── GtrStartupAnimation
│   ├── StartupAudioController
│   └── StartupState
├── authentication/
│   ├── LoginScreen
│   ├── LoginViewModel
│   ├── AuthenticationRepository
│   ├── AuthenticationUseCase
│   └── AuthenticationState
├── authorization/
│   ├── RoleRouter
│   ├── PermissionManager
│   ├── PermissionEvaluator
│   ├── RoleRepository
│   ├── PermissionRepository
│   └── AccessPolicy
├── session/
│   ├── SessionManager
│   ├── SessionRepository
│   ├── SessionTimeoutController
│   └── ActiveSession
├── kiosk/
│   ├── KioskController
│   ├── DevicePolicyController
│   ├── LockTaskController
│   └── DeviceAdministrationController
├── maintenance/
│   ├── MaintenanceLoginScreen
│   ├── DeviceAdministrationScreen
│   ├── MaintenanceViewModel
│   └── MaintenanceAuditLogger
├── pos/
│   ├── PosScreen
│   ├── PosViewModel
│   └── Existing POS Components
└── dashboard/
    ├── SalesDashboard
    ├── InventoryDashboard
    ├── ManagerDashboard
    └── Existing Role Dashboards
```

The actual project structure must follow the existing architecture where it already provides equivalent components.

# 19. Recommended Domain Models

The implementation should support models conceptually similar to:

```kotlin
data class StaffUser(
    val staffId: String,
    val displayName: String,
    val roleId: String,
    val branchId: String?,
    val departmentId: String?,
    val active: Boolean
)
```

```kotlin
data class UserRole(
    val roleId: String,
    val roleName: String,
    val defaultDestination: String,
    val active: Boolean
)
```

```kotlin
data class Permission(
    val permissionId: String,
    val module: String,
    val resource: String,
    val action: String
)
```

```kotlin
data class EffectiveAccessPolicy(
    val userId: String,
    val roleId: String,
    val defaultDestination: String,
    val permissions: Set<String>,
    val dataScope: String
)
```

These models are conceptual only. Adapt them to the existing domain model and database schema. Do not duplicate existing user, role or permission models.

# 20. Role Routing Requirements

The routing implementation must:

- Use the authenticated user's effective access policy.
- Resolve the configured default destination.
- Validate that the destination is authorized.
- Prevent routing to an unauthorized destination.
- Support role changes without requiring a new application build.
- Support future roles.
- Support role-specific landing screens.
- Support user-specific permission overrides where the architecture allows.
- Handle disabled or inactive roles.
- Handle missing role configuration safely.

# 21. Acceptance Criteria

The implementation is complete only when all of the following are functional.

## Startup

- [ ] The Nissan GTR Auto application automatically launches after device boot.
- [ ] The standard Android launcher is not displayed during normal startup.
- [ ] The application opens in full-screen kiosk mode.
- [ ] The Nissan GTR Auto startup experience is displayed.
- [ ] The animated GT-R sequence plays correctly.
- [ ] Startup engine audio is synchronized with the animation.
- [ ] Audio can be enabled or disabled.
- [ ] The animation transitions smoothly to the staff login screen.
- [ ] The animation does not unnecessarily replay after ordinary logout.

## Authentication

- [ ] Staff can authenticate using the configured login method.
- [ ] Invalid credentials are rejected.
- [ ] Credentials are not stored in plaintext.
- [ ] Failed attempts are handled securely.
- [ ] Sessions are created securely.
- [ ] Inactive staff cannot access the system.

## Role Routing

- [ ] Staff role is loaded after authentication.
- [ ] Effective permissions are loaded.
- [ ] Salespersons are routed directly to the POS screen.
- [ ] Other roles are routed to their configured dashboards.
- [ ] No user is routed to an unauthorized screen.
- [ ] Missing role configuration is handled safely.
- [ ] Role destinations can be configured without rebuilding the application.

## Permissions

- [ ] Unauthorized modules are hidden.
- [ ] Unauthorized screens cannot be opened.
- [ ] Unauthorized actions are rejected.
- [ ] Data-scope restrictions are applied.
- [ ] Backend authorization is enforced where applicable.
- [ ] Permission checks are not limited to user interface visibility.

## Kiosk

- [ ] Android home access is blocked during normal operation.
- [ ] Unauthorized applications are blocked.
- [ ] Android notifications are not exposed to ordinary staff.
- [ ] Android settings are restricted.
- [ ] The POS application remains the dedicated business application.
- [ ] The terminal returns to the application after restart.

## Maintenance Access

- [ ] Authorized personnel can enter maintenance mode.
- [ ] Ordinary staff cannot access maintenance functions.
- [ ] Device administration functions are permission-controlled.
- [ ] Maintenance sessions are logged.
- [ ] Maintenance sessions expire after configured inactivity.
- [ ] The terminal returns to kiosk mode after maintenance access ends.

# 22. Final Expected Behaviour

```text
The tablet is powered on.

The Nissan GTR Auto branded startup experience begins.

An animated Nissan GT-R appears and accelerates across the screen.

The synchronized GT-R engine startup and acceleration sound plays if audio is enabled.

The Nissan GTR Auto logo is revealed.

The system automatically transitions to the Nissan GTR Auto staff login screen.

A staff member enters authorized credentials.

The system authenticates the staff member.

The system loads the staff profile, role and effective permissions.

If the staff member is a salesperson:
    Open the POS sales screen immediately.

If the staff member has another role:
    Open the role's configured dashboard.

Only authorized modules, screens, actions and data are available.

The standard Android interface remains unavailable during ordinary operation.

Authorized personnel may enter a secure maintenance mode to access approved device functions.

When the user logs out:
    End the secure session.
    Return to the Nissan GTR Auto login screen.
    Keep the device in dedicated kiosk mode.

The tablet must operate as a secure, branded Nissan GTR Auto business terminal rather than as an unrestricted consumer Android tablet.
```

# 23. Implementation Instructions for Cursor

Implement this functionality incrementally.

Before changing code:

1. Analyze the existing architecture.
2. Identify existing authentication and role logic.
3. Identify existing POS navigation.
4. Identify existing dashboard navigation.
5. Identify existing permission enforcement.
6. Identify existing Android startup and kiosk configuration.
7. Produce an implementation plan based on the actual project structure.

During implementation:

- Reuse existing models where possible.
- Extend existing repositories rather than duplicating them.
- Preserve working POS functionality.
- Preserve existing database data.
- Preserve existing API contracts unless changes are required.
- Add migrations where database changes are required.
- Add tests for authentication.
- Add tests for role routing.
- Add tests for permission enforcement.
- Add tests for session expiration.
- Add tests for unauthorized access.
- Add tests for kiosk recovery where practical.

Do not:

- Replace the entire application architecture.
- Remove existing features.
- Hard-code administrator access.
- Hard-code all role destinations.
- Store PINs or passwords in plaintext.
- Grant unrestricted access when permissions cannot be loaded.
- Use only hidden UI buttons as permission enforcement.
- Root the device.
- Modify the bootloader.
- Implement unsafe firmware modifications.
- Use unlicensed Nissan or GT-R assets.

After implementation:

1. Build the project.
2. Resolve compilation errors.
3. Resolve dependency conflicts.
4. Run automated tests.
5. Verify startup flow.
6. Verify login flow.
7. Verify salesperson direct routing.
8. Verify all role dashboards.
9. Verify permission restrictions.
10. Verify kiosk restrictions.
11. Verify maintenance access.
12. Verify logout and session timeout.
13. Verify application recovery after restart.
14. Document all new files and modified files.
15. Explain any architecture decisions and required device-owner provisioning steps.
