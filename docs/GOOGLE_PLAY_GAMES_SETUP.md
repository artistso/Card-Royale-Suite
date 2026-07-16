# Google Play Games Services setup

The source contains a `PlayGamesGateway` interface and an offline stub so the project runs before cloud credentials exist.

## Production checklist

1. Create the application in Google Play Console.
2. Configure Play Games Services v2.
3. Link the Android application package and signing certificate fingerprints.
4. Add achievements and leaderboards in the console.
5. Replace the local gateway with a `PlayGamesSdkGateway` implementation.
6. Request automatic sign-in state on launch.
7. Send a server authentication code to the application backend.
8. Resolve and validate the player identity server-side.
9. Submit only server-verified ranked scores and achievements.
10. Test with licensed tester accounts before release.

## Security rule

Do not trust a player identifier supplied as ordinary client JSON. Resolve identity using the server-side authentication flow and bind it to the backend session.
