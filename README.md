# sonnen_battery
Hubitat Driver for Sonnen Battery

Programmed for Sonnen Core. Should work with other Sonnen batteries (untested).

Requires only local IP address of your Sonnen battery

Refreshes data up to once per minute

If driver is unable to retrieve update from battery, connected state will change to "false"

Setup Instructions:
1. Install the driver
2. Create a virtual device
3. Assign the Sonnen Battery driver
4. Enter your battery IP (https://find-my.sonnen-batterie.com/)
5. Click Refresh to intiate refresh interval

Energy Flow Diagram
1. Add a new tile to your Dashboard
2. Select your virtual battery device
3. Choose the Attribute template
4. Pick the flow_tile attribute
