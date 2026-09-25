# Sonnen Battery - Hubitat
Hubitat driver for a Sonnen battery, using the battery's local API (v2).

Written for Sonnen Core. Might work with other models.

# Setup Instructions:
1. Install the driver (Hubitat Package Manager, or paste `sonnen_battery_driver.groovy` into Drivers Code)
2. Create a virtual device
3. Assign the Sonnen Battery driver
4. Enter your battery's LAN IP (https://find-my.sonnen-batterie.com/)
5. Enter your API key (Sonnen dashboard → Software-Integration)
6. Choose a refresh interval
7. Save Preferences

# How it polls
- Each refresh makes one call to `/api/v2/status`.
- Once a day (and when preferences are saved) the driver also reads the battery's
  rated capacity from `/api/v2/latestdata` and battery health from `/api/v2/battery`.
  These need the API key.
- All requests are asynchronous with a 10 second timeout.

# Attributes
| Attribute | Unit | Description |
|---|---|---|
| `battery` | % | Usable state of charge (USOC) |
| `Production_W` | W | Solar production |
| `Consumption_W` | W | House consumption |
| `GridFeedIn_W` | W | Grid power: positive = exporting, negative = importing |
| `Pac_total_W` | W | Battery power: positive = discharging, negative = charging |
| `power` | W | Production minus consumption |
| `energy` | kW | Production minus consumption, as a point-in-time value in kW (not a kWh total) |
| `powerSource` | | `battery` when discharging, otherwise `mains` |
| `BackupBuffer` | % | Backup buffer reserved for outages |
| `OperatingMode` | | Sonnen operating mode |
| `SystemStatus` | | Sonnen system status, e.g. `OnGrid` |
| `FullChargeCapacity` | Wh | Rated full-charge capacity (daily) |
| `RemainingCapacity_Wh` | Wh | Usable energy currently stored |
| `MinutesToCharge` | min | Estimated time to full at the current charge rate |
| `MinutesToDischarge` | min | Estimated time to empty at the current discharge rate |
| `StateOfHealth` | % | Remaining capacity compared with new (daily) |
| `CycleCount` | | Full charge cycles (daily) |
| `MaxCellTemperature` / `MinCellTemperature` | °C | Cell temperatures at the time of the daily check |
| `healthStatus` | | `online`, or `offline` after 3 failed polls in a row |

# Commands
- **Refresh**: poll the battery now.
- **Set Backup Buffer**: set the backup buffer percentage. Accepts `0` (disabled) or a
  whole number from `5` to `100`.

# Energy Flow Diagram
1. Enable **Display Flow Tiles** in the device preferences
2. Add a new tile to your Dashboard
3. Select your battery device
4. Choose the Attribute template
5. Pick `flow_tile_large` or `flow_tile_small`

The tiles use emoji and Unicode arrows, so they need no internet access.
Green arrows show solar and battery flows; red arrows show power drawn from the grid.

# Child Devices
Enable **Enable Child Devices** to create six component devices (production,
consumption, to/from grid, to/from battery). Each reports its flow as `energy` in kW.
Turning the option off and saving preferences removes the child devices.
