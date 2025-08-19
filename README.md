VoXNet is an Android app that connects with an ESP32 over Wi-Fi for voice calls, SMS, SOS, and GPS tracking, designed for critical field communication even without Internet.

Features:

1. Two-way voice calls: Near-instant audio over Wi-Fi (PCM16, 8kHz)
2. SOS & SMS: Send alerts and messages through the ESP32
3. GPS path tracking: View location and history on a fully offline map (MBTiles)
4. Connection timeline: Live event log of all actions and statuses
5. Speaker toggle: Switch between earpiece and loudspeaker during calls

Requirements:

1. An ESP32 device running server firmware with:
2. Control WebSocket at /ctrl for call, SMS, SOS, GPS, and status messages
3. Voice WebSocket at /voice for real-time PCM16 audio streaming
4. (Optional) Copy area.mbtiles to Internal storage/osmdroid/ for offline maps

How to Use:

1. Wi-Fi: Connect your Android device to the ESP32 Wi-Fi network
2.  Settings: Enter the ESP32’s IP address/hostname and confirm WebSocket ports/paths
3.  Connect: Tap CONNECT; wait for status to change
4.  Calling: Enter a phone number and tap CALL (live call status displayed)
5.  SMS/SOS: Enter details and tap SEND SMS or SOS
6.  Map: Tap OPEN MAP (with area.mbtiles present) to see offline GPS tracking

Permissions:

1. Microphone: Required for voice calling
2. Storage (optional): Required to read MBTiles from the osmdroid folder for offline map viewing

Notes / Troubleshooting:

1. If using MBTiles made with JPEG tiles, change .png to .jpg in MapActivity.kt for compatibility
2. If OPEN MAP shows a blank grid, verify area.mbtiles is in the correct location and format
