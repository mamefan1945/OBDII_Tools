# Security

## Reporting a vulnerability

Please do not publish exploit details or credentials in a public issue. Use GitHub's private vulnerability reporting feature for this repository when available.

## Application data

OBDII Tools can collect vehicle identifiers, diagnostic trouble codes, telemetry, and trip history. Backup rules intentionally exclude the app database and DataStore preferences from cloud backup and device transfer. This also means those items are not restored from Android backup.

## API keys

The Google Maps key is packaged into the Android application at build time and must be treated as a public client key, not a secret. Restrict it in Google Cloud Console by Android application ID, signing-certificate SHA-1, and only the APIs this application uses. Never commit service-account keys or unrestricted server credentials.

## Diagnostic command safety

Custom PID commands are restricted to four hexadecimal characters for standard Mode 01 PID requests. Do not add arbitrary AT-command execution to user-editable fields.
