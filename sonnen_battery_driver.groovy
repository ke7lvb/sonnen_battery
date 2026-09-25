metadata {
    definition(
        name: "Sonnen Battery (Optimized Polling)",
        namespace: "ke7lvb",
        author: "Ryan Lundell"
    ) {
        capability "PowerSource"
        capability "PowerMeter"
        capability "Battery"
        capability "Actuator"
        capability "Refresh"
        capability "EnergyMeter"

        attribute "energy", "number"
        attribute "BackupBuffer", "number"
        attribute "Consumption_W", "number"
        attribute "GridFeedIn_W", "number"
        attribute "OperatingMode", "number"
        attribute "Pac_total_W", "number"
        attribute "Production_W", "number"
        attribute "SystemStatus", "string"
        attribute "MinutesToCharge", "number"
        attribute "MinutesToDischarge", "number"
        attribute "FullChargeCapacity", "number"
        attribute "RemainingCapacity_Wh", "number"


        if (flowTiles) {
            attribute "flow_tile_large", "string"
            attribute "flow_tile_small", "string"
        }

        command "setBackupBuffer", [[name: "Backup Buffer*", type: "NUMBER"]]
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true
        input name: "battery_ip_address", type: "string", title: "Sonnen battery LAN IP", required: true

        input("refresh_interval", "enum", title: "Refresh Interval", required: true, defaultValue: "0", options: [
            0: "Do NOT update",
            10: "Every 10 seconds / 10 minutes / 4 hours",
            15: "Every 15 seconds / 15 minutes / 6 hours",
            20: "Every 20 seconds / 20 minutes / 8 hours",
            30: "Every 30 seconds / 30 minutes / 12 hours",
            1: "Every 60 seconds / 60 minutes / 24 hours"
        ])

        input("refresh_interval_unit", "enum", title: "Refresh Interval Unit", required: true, defaultValue: "1", options: [
            1: "Seconds",
            2: "Minutes",
            3: "Hours"
        ])

        input name: "flowTiles", type: "bool", title: "Display Flow Tiles", defaultValue: false
        input name: "enableChildDevices", type: "bool", title: "Enable Child Devices", defaultValue: false
        input name: "apiKey", type: "string", title: "API Key", required: true
    }
}

def installed() {
    if (logEnable) log.info "Driver installed"
    state.version = "1.6.0"
}

def updated() {
    if (logEnable) log.info "Settings updated"
    unschedule()

    if (settings.refresh_interval != "0") {
        scheduleRefreshJob()
    } else {
        if (logEnable) log.info "Polling disabled"
    }

    refreshCapacity()

    state.remove("USOC")
    state.remove("BatteryCharging")
    state.remove("BatteryDischarging")
    state.remove("FlowGridBattery")
    state.version = "1.6.0"
}

def scheduleRefreshJob() {
    def interval = settings.refresh_interval
    def unit = settings.refresh_interval_unit

    if (unit == "1") { // seconds
        if (interval == "1") {
            schedule("13 */1 * ? * * *", refresh)
        } else {
            schedule("3/${interval} * * ? * * *", refresh)
        }
    } else if (unit == "2") { // minutes
        if (interval == "1") {
            schedule("13 1 */1 ? * * *", refresh)
        } else {
            schedule("13 */${interval} * ? * * *", refresh)
        }
    } else { // hours
        if (interval == "1") {
            schedule("13 1 12 ? * * *", refresh)
        } else {
            schedule("13 1 */${interval} ? * * *", refresh)
        }
    }

    if (logEnable) log.info "Polling scheduled"
}

/* ---------------------------------------------------------
   MAIN REFRESH — /status ONLY (async, no API key needed)
--------------------------------------------------------- */
def refresh() {
    asynchttpGet("handleStatus", [
        uri: "http://${battery_ip_address}/api/v2/status",
        contentType: "application/json",
        timeout: 10
    ])

    // Rated capacity rarely changes: refresh it at most once a day
    def dayMs = 24 * 60 * 60 * 1000
    if (state.FullChargeCapacity == null || now() - (state.lastCapacityCheck ?: 0) > dayMs) {
        refreshCapacity()
    }
}

def handleStatus(resp, data) {
    if (resp.hasError()) {
        log.error "Error calling status: ${resp.getErrorMessage()}"
        return
    }
    if (resp.status != 200) {
        log.error "Error calling status: HTTP ${resp.status}"
        return
    }

    def status = resp.json
    processStatus(status)

    if (flowTiles) updateTiles(status)
    if (enableChildDevices) updateChildDevices(status)
    if (state.FullChargeCapacity) estimateCharge(status)
}

/* ---------------------------------------------------------
   CAPACITY — /latestdata ONCE A DAY (async, requires API key)
--------------------------------------------------------- */
def refreshCapacity() {
    if (!battery_ip_address || !apiKey) return
    state.lastCapacityCheck = now()

    asynchttpGet("handleLatest", [
        uri: "http://${battery_ip_address}/api/v2/latestdata",
        contentType: "application/json",
        headers: ['Auth-Token': apiKey],
        timeout: 10
    ])
}

def handleLatest(resp, data) {
    if (resp.hasError()) {
        log.error "Error calling latestdata: ${resp.getErrorMessage()}"
        return
    }
    if (resp.status != 200) {
        log.error "Error calling latestdata: HTTP ${resp.status}"
        return
    }

    def latest = resp.json
    if (latest?.FullChargeCapacity != null) {
        state.FullChargeCapacity = latest.FullChargeCapacity
        sendEvent(name: "FullChargeCapacity", value: state.FullChargeCapacity)
        if (logEnable) log.info "FullChargeCapacity updated: ${state.FullChargeCapacity} Wh"
    }
}

/* ---------------------------------------------------------
   PROCESS STATUS
--------------------------------------------------------- */
def processStatus(data) {

    sendEvent(name: "Production_W", value: data.Production_W)
    sendEvent(name: "Consumption_W", value: data.Consumption_W)
    sendEvent(name: "GridFeedIn_W", value: data.GridFeedIn_W)
    sendEvent(name: "Pac_total_W", value: data.Pac_total_W)

    if (data.USOC != null)
        sendEvent(name: "battery", value: data.USOC)

    def power = (data.Production_W ?: 0) - (data.Consumption_W ?: 0)
    sendEvent(name: "power", value: power)
    sendEvent(name: "energy", value: (power / 1000))

    sendEvent(name: "powerSource", value: data.BatteryDischarging ? "battery" : "mains")

    if (data.BackupBuffer != null)
        sendEvent(name: "BackupBuffer", value: data.BackupBuffer)

    if (data.OperatingMode != null)
        sendEvent(name: "OperatingMode", value: data.OperatingMode)

    if (data.SystemStatus != null)
        sendEvent(name: "SystemStatus", value: data.SystemStatus)
}

/* ---------------------------------------------------------
   TILES — USE STATUS DATA
--------------------------------------------------------- */
//icon helpers
def iconSun()  { "<img src='https://img.icons8.com/material-outlined/48/4a90e2/sun--v1.png'/>" }
def iconHome() { "<img src='https://img.icons8.com/material-outlined/48/4a90e2/cottage.png'/>" }
def iconBatt() { "<img src='https://img.icons8.com/ios-glyphs/48/4a90e2/battery--v1.png'/>" }
def iconGrid() { "<img src='https://img.icons8.com/ios/48/4a90e2/transmission-tower.png'/>" }

def iconSuns()  { iconSun().replace("48","24") }
def iconHomes() { iconHome().replace("48","24") }
def iconBatts() { iconBatt().replace("48","24") }
def iconGrids() { iconGrid().replace("48","30") }

def arrowLD() { "<img src='https://img.icons8.com/material-sharp/48/26e07f/left-down2.png'/>" }
def arrowRD() { "<img src='https://img.icons8.com/material-outlined/48/26e07f/right-down2.png'/>" }
def arrowDown() { "<img src='https://img.icons8.com/material-rounded/48/26e07f/long-arrow-down.png'/>" }
def arrowLeft() { "<img src='https://img.icons8.com/material-rounded/48/26e07f/long-arrow-left.png'/>" }
def arrowUpRed() { "<img src='https://img.icons8.com/material-outlined/48/fa314a/left-up2.png'/>" }
def arrowUpRightRed() { "<img src='https://img.icons8.com/material-outlined/48/fa314a/right-up2.png'/>" }
def arrowDL() { "<img src='https://img.icons8.com/material-outlined/48/26e07f/down-left.png'/>" }

def arrowLDs() { arrowLD().replace("48","24") }
def arrowRDs() { arrowRD().replace("48","24") }
def arrowDowns() { arrowDown().replace("48","24") }
def arrowLefts() { arrowLeft().replace("48","24") }
def arrowUpReds() { arrowUpRed().replace("48","24") }
def arrowUpRightReds() { arrowUpRightRed().replace("48","24") }
def arrowDLs() { arrowDL().replace("48","24") }


def updateTiles(data) {
    def prod = data.Production_W ?: 0
    def cons = data.Consumption_W ?: 0
    def grid = data.GridFeedIn_W ?: 0
    def pac  = data.Pac_total_W ?: 0

    // Flow directions reported by the battery
    def fCP = data.FlowConsumptionProduction
    def fPB = data.FlowProductionBattery
    def fPG = data.FlowProductionGrid
    def fCB = data.FlowConsumptionBattery
    def fCG = data.FlowConsumptionGrid
    def fGB = data.FlowGridBattery

    def large = "<div><table style='margin:auto'>"
    large += "<tr><td></td><td></td><td>${formatEnergy(prod)}</td><td></td><td></td></tr>"
    large += "<tr><td></td><td>${fCP ? arrowLD() : ''}</td><td>${iconSun()}</td><td>${fPB ? arrowRD() : ''}</td><td></td></tr>"
    large += "<tr><td>${formatEnergy(cons)}</td><td>${iconHome()}</td><td>${fPG ? arrowDown() : ''}${fCB ? arrowLeft() : ''}</td><td>${iconBatt()}</td><td>${formatEnergy(pac)}</td></tr>"
    large += "<tr><td></td><td>${fCG ? arrowUpRed() : ''}</td><td>${iconGrid()}</td><td>${fGB ? arrowUpRightRed() : ''}</td><td></td></tr>"
    large += "<tr><td></td><td></td><td>${formatEnergy(grid)}</td><td></td><td></td></tr>"
    large += "</table></div>"

    sendEvent(name: "flow_tile_large", value: large)

    def small = "<div><table style='margin:auto'>"
    small += "<tr><td>${fCP ? arrowLDs() : ''}</td><td>${iconSuns()}</td><td>${fPB ? arrowRDs() : ''}</td></tr>"
    small += "<tr><td>${iconHomes()}</td><td>${fPG ? arrowDowns() : ''}${fCB ? arrowLefts() : ''}</td><td>${iconBatts()}</td></tr>"
    small += "<tr><td>${fCG ? arrowUpReds() : ''}</td><td>${iconGrids()}</td><td>${fGB ? arrowUpRightReds() : ''}</td></tr>"
    small += "</table></div>"

    sendEvent(name: "flow_tile_small", value: small)
}


/* ---------------------------------------------------------
   CHILD DEVICES — USE STATUS DATA
--------------------------------------------------------- */
def updateChildDevices(data) {
    def prod = data.Production_W ?: 0
    def cons = data.Consumption_W ?: 0
    def grid = data.GridFeedIn_W ?: 0
    def pac  = data.Pac_total_W ?: 0

    child("Sonnen Total Production").parse([[name: "energy", value: prod / 1000]])
    child("Sonnen Total Consumption").parse([[name: "energy", value: cons / 1000]])
    child("Sonnen Energy to Grid").parse([[name: "energy", value: [grid / 1000, 0].max()]])
    child("Sonnen Energy from Grid").parse([[name: "energy", value: [-grid / 1000, 0].max()]])
    child("Sonnen Energy from Battery").parse([[name: "energy", value: [pac / 1000, 0].max()]])
    child("Sonnen Energy to Battery").parse([[name: "energy", value: [-pac / 1000, 0].max()]])
}

def child(name) {
    def d = getChildDevice(name)
    if (!d) d = addChildDevice("hubitat", "Generic Component Energy Meter", name, [name: name, isComponent: false])
    return d
}

/* ---------------------------------------------------------
   CHARGE ESTIMATION
--------------------------------------------------------- */
def estimateCharge(data) {
    def cap = state.FullChargeCapacity ?: 0        // Wh
    def usoc = data.USOC ?: 0                      // %
    def pac = data.Pac_total_W ?: 0                // W (+ discharge, - charge)

    // Wh currently stored
    def remainingWh = Math.round(cap * (usoc / 100))
    sendEvent(name: "RemainingCapacity_Wh", value: remainingWh)


    // Wh needed to reach 100%
    def neededWh = Math.round(cap - remainingWh)

    // Time to full (minutes)
    def tCharge = 0
    if (data.BatteryCharging && pac < 0) {
        def chargePower = Math.abs(pac)   // convert negative to positive
        tCharge = Math.round((neededWh / chargePower) * 60)
    }

    // Time to empty (minutes)
    def tDischarge = 0
    if (data.BatteryDischarging && pac > 0) {
        def dischargePower = pac
        tDischarge = Math.round((remainingWh / dischargePower) * 60)
    }

    sendEvent(name: "MinutesToCharge", value: tCharge)
    sendEvent(name: "MinutesToDischarge", value: tDischarge)
}


/* ---------------------------------------------------------
   BACKUP BUFFER COMMAND
--------------------------------------------------------- */
def setBackupBuffer(buffer) {
    try {
        httpPut([
            uri: "http://${battery_ip_address}/api/v2/configurations",
            body: [EM_USOC: buffer],
            contentType: "application/json",
            headers: ['Auth-Token': apiKey]
        ]) { resp ->
            if (resp.status == 200) log.info "BackupBuffer updated"
        }
    } catch (e) {
        log.error "BackupBuffer error: ${e.message}"
    }
}

/* ---------------------------------------------------------
   HELPERS
--------------------------------------------------------- */
def formatEnergy(e) {
    if (e == null) return "0 W"
    if (e.abs() < 1000) return "${e} W"
    if (e.abs() < 1_000_000) return "${(e/1000).toDouble().round(2)} kW"
    return "${(e/1_000_000).toDouble().round(2)} MW"
}
