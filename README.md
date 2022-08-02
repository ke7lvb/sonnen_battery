# Sonnen Battery - Hubitat
Hubitat Driver for Sonnen Battery

Simple driver to retrieve the current status of your Sonnen Battery

Written for Sonnen Core. Might work with other models.

Requires only local IP address of your Sonnen battery


# Setup Instructions:
1. Install the driver
2. Create a virtual device
3. Assign the Sonnen Battery driver
4. Enter your battery IP (https://find-my.sonnen-batterie.com/)
5. Save Preferences

# Energy Flow Diagram
1. Add a new tile to your Dashboard
2. Select your virtual battery device
3. Choose the Attribute template
4. Pick the flow_tile attribute


The tool can make calls to change the charge/discharge rate of the battery (uses the v1 API at the moment). In my experience, these changes last about 30 seconds before normal function resumes if the battery is set to auto mode. In manual mode the changes remain in effect until another API call is made.
