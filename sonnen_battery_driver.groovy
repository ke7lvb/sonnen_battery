metadata {
    definition(
        name: "Sonnen Battery",
        namespace: "ke7lvb",
        author: "Ryan Lundell",
        importUrl: "https://raw.githubusercontent.com/ke7lvb/sonnen_battery/main/sonnen_battery_driver.groovy",
    ) {
        capability "PowerSource"
        capability "PowerMeter"
        capability "Battery"
        //capability "VoltageMeasurement"
        capability "Actuator"
        capability "Refresh"
        capability "EnergyMeter"

        attribute "energy", "number"

        if(flowTiles) command "updateTiles"
        command "setBackupBuffer", [
            [name: "Set Battery Backup Buffer*", type: "NUMBER", description: "Sets the battery backup buffer. Requires an API key to be configured in Preferences"]
        ]
        command "getFullChargeCapacity", [
        	[name: "Get Battery Full Charge Capacity", type: "", description: "Used to calculate the number of minutes until fully charged/discharged. Requires an API key to be configured in Preferences"]
        ]

        attribute "BackupBuffer", "number"
        //attribute "BatteryCharging", "string"
        //attribute "BatteryDischarging", "string"
        //attribute "Consumption_Avg", "number"
        attribute "Consumption_W", "number"
        //attribute "Fac", "number"
        //attribute "FlowConsumptionBattery", "string"
        //attribute "FlowConsumptionGrid", "string"
        //attribute "FlowConsumptionProduction", "string"
        //attribute "FlowGridBattery", "string"
        //attribute "FlowProductionBattery", "string"
        //attribute "FlowProductionGrid", "string"
        attribute "GridFeedIn_W", "number"
        //attribute "IsSystemInstalled", "number"
        //attribute "OperatingMode", "number"
        attribute "Pac_total_W", "number"
        attribute "Production_W", "number"
        //attribute "RSOC", "number"
        //attribute "RemainingCapacity_W", "number"
        attribute "SystemStatus", "string"
        //attribute "Timestamp", "string"
        //attribute "USOC", "number"
        //attribute "Uac", "number"
        //attribute "Ubat", "number"
        attribute "TimeToCharge", "number"
        attribute "TimeToDischarge", "number"
        if(flowTiles){
            attribute "flow_tile_large", "string"
            attribute "flow_tile_small", "string"
        }
        //attribute "lanConnected", "string"
    }
    preferences {
        input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true, description: ""
        input name: "battery_ip_address", type: "string", title: "Sonnen battery LAN IP", description: "example: 192.168.0.2", required: true
        input("refresh_interval", "enum", title: "How often to refresh the battery data. Used in combintation with Refresh Interval Unit.", required: true, defaultValue: "0", options: [
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
        input name: "flowTiles", type: "bool", title: "Display Flow Tiles", description: "html markup showing direction of energy flow", defaultValue: false
        input name: "enableChildDevices", type: "bool", title: "Enable Child Devices", defaultValue: false, description: "If you would like individual devices for different power readings"
		input name: "apiKey", type: "string", title: "API Key", description: "Required for all actions except Refresh"
    }
}

def version() {
    return "1.4.1"
}

def installed() {
    if (logEnable) log.info "Driver installed"

    state.version = version()
}

def uninstalled() {
    unschedule(refresh)
    if (logEnable) log.info "Driver uninstalled"
}

def updated() {
    if (logEnable) log.info "Settings updated"
    if (settings.refresh_interval != "0") {
        if(settings.refresh_interval_unit == "1"){
            //every minute
            if(settings.refresh_interval == "1"){
                schedule("13 */${settings.refresh_interval} * ? * * *", refresh, [overwrite: true])
                if (logEnable) log.info "Job scheduled 13 */${settings.refresh_interval} * ? * * *"
            }else{
                //every x seconds
                schedule("3/${settings.refresh_interval} * * ? * * *", refresh, [overwrite: true])
                if (logEnable) log.info "Job scheduled 3/${settings.refresh_interval} * * ? * * *"
            }
        }else if(settings.refresh_interval_unit == "2"){
            //every hour
            if(settings.refresh_interval == "1"){
                schedule("13 1 */${settings.refresh_interval} ? * * *", refresh, [overwrite: true])
                if (logEnable) log.info "Job scheduled 13 1 */${settings.refresh_interval} ? * * *"
            }else{
                //every x minutes
                schedule("13 */${settings.refresh_interval} * ? * * *", refresh, [overwrite: true])
                if (logEnable) log.info "Job scheduled 13 */${settings.refresh_interval} * ? * * *"
            }
        }else{
            //every day
            if(settings.refresh_interval == "1"){
                schedule("13 1 12  ? * * *", refresh, [overwrite: true])
                if (logEnable) log.info "Job scheduled 13 1 12 ? * * *"
            }else{
                //every x hours
                schedule("13 1 */${settings.refresh_interval} ? * * *", refresh, [overwrite: true])
                if (logEnable) log.info "Job scheduled 13 1 */${settings.refresh_interval} ? * * *"
            }
        }
    }else{
        unschedule(refresh)
        if (logEnable) log.info "Removed scheduled job"
    }
    state.version = version()
}

def refresh() {
    def params = [
        uri: "http://${battery_ip_address}/api/v2/status",
        contentType: "application/json"
    ]

    try {
        httpGet(params) { response ->
            if (response.status == 200) {
                def respData = response.data
                
                state.BackupBuffer = respData.BackupBuffer
                sendEvent(name: "BackupBuffer", value: state.BackupBuffer)

                state.BatteryCharging = respData.BatteryCharging
                state.BatteryDischarging = respData.BatteryDischarging
                state.Consumption_Avg = respData.Consumption_Avg
                state.Consumption_W = respData.Consumption_W
                sendEvent(name: "Consumption_W", value: state.Consumption_W)

                state.Fac = respData.Fac
                state.FlowConsumptionBattery = respData.FlowConsumptionBattery
                state.FlowConsumptionGrid = respData.FlowConsumptionGrid
                state.FlowConsumptionProduction = respData.FlowConsumptionProduction
                state.FlowGridBattery = respData.FlowGridBattery
                state.FlowProductionBattery = respData.FlowProductionBattery
                state.FlowProductionGrid = respData.FlowProductionGrid

                state.GridFeedIn_W = respData.GridFeedIn_W
                sendEvent(name: "GridFeedIn_W", value: state.GridFeedIn_W)

                state.IsSystemInstalled = respData.IsSystemInstalled
                state.OperatingMode = respData.OperatingMode
                state.Pac_total_W = respData.Pac_total_W
                sendEvent(name: "Pac_total_W", value: state.Pac_total_W)

                state.Production_W = respData.Production_W
                sendEvent(name: "Production_W", value: state.Production_W)

                state.RSOC = respData.RSOC
                state.RemainingCapacity_W = respData.RemainingCapacity_Wh
                state.SystemStatus = respData.SystemStatus
                sendEvent(name: "SystemStatus", value: state.SystemStatus)

                state.Timestamp = respData.Timestamp
                state.USOC = respData.USOC
                state.Uac = respData.Uac
                state.Ubat = respData.Ubat

                state.battery = respData.USOC
                sendEvent(name: "battery", value: state.battery)

                state.power = (respData.Production_W - respData.Consumption_W)
                sendEvent(name: "power", value: state.power)
                sendEvent(name: "energy", value: (state.power / 1000))

                state.powerSource = respData.FlowConsumptionBattery ? "battery" : "mains"
                sendEvent(name: "powerSource", value: state.powerSource)

                state.lanConnected = true
                if (logEnable) log.info respData
            } else {
                log.error "Failed to retrieve system status. Status: ${response.status}"
                state.lanConnected = false
            }
        }
    } catch (Exception e) {
        log.error "Error fetching system status: ${e.message}"
        state.lanConnected = false
    }

    
    if(flowTiles){
        updateTiles()
    }
    
    if(enableChildDevices){
        def totalProduction = getChildDevice("Sonnen Total Production")
        if (!totalProduction) {
            totalProduction = addChildDevice("hubitat", "Generic Component Energy Meter", "Sonnen Total Production", [name: "Sonnen Total Production", isComponent: false])
        }
        totalProduction.parse([[name: "energy", value: (state.Production_W / 1000)]])
        
        def totalConsumption = getChildDevice("Sonnen Total Consumption")
        if (!totalConsumption) {
            totalConsumption = addChildDevice("hubitat", "Generic Component Energy Meter", "Sonnen Total Consumption", [name: "Sonnen Total Consumption", isComponent: false])
        }
        totalConsumption.parse([[name: "energy", value: (state.Consumption_W / 1000)]])
        
        def energyToGrid = getChildDevice("Sonnen Energy to Grid")
        if (!energyToGrid) {
            energyToGrid = addChildDevice("hubitat", "Generic Component Energy Meter", "Sonnen Energy to Grid", [name: "Sonnen Energy to Grid", isComponent: false])
        }
        energyToGrid.parse([[name: "energy", value: convertEnergy(state.GridFeedIn_W / 1000)]])
        
        def energyFromoGrid = getChildDevice("Sonnen Energy from Grid")
        if (!energyFromoGrid) {
            energyFromoGrid = addChildDevice("hubitat", "Generic Component Energy Meter", "Sonnen Energy from Grid", [name: "Sonnen Energy from Grid", isComponent: false])
        }
        energyFromoGrid.parse([[name: "energy", value: convertEnergy(state.GridFeedIn_W / 1000 * -1)]])
        
        def energyFromBattery = getChildDevice("Sonnen Energy from Battery")
        if (!energyFromBattery) {
            energyFromBattery = addChildDevice("hubitat", "Generic Component Energy Meter", "Sonnen Energy from Battery", [name: "Sonnen Energy from Battery", isComponent: false])
        }
        energyFromBattery.parse([[name: "energy", value: convertEnergy(state.Pac_total_W / 1000)]])
        
        def energyToBattery = getChildDevice("Sonnen Energy to Battery")
        if (!energyToBattery) {
            energyToBattery = addChildDevice("hubitat", "Generic Component Energy Meter", "Sonnen Energy to Battery", [name: "Sonnen Energy to Battery", isComponent: false])
        }
        energyToBattery.parse([[name: "energy", value: convertEnergy(state.Pac_total_W / 1000 * -1)]])
    }
    
    if (state.FullChargeCapacity != null && state.FullChargeCapacity.toString().trim()) {
        estimateCharge()
    }

}

def updateTiles() {
    operating_mode = [
        1: "Manual",
        2: "Self-Consumption",
        7: "Backup Power - US",
        10: "TOU"
    ];
    om = state.OperatingMode.toInteger()
    if(logEnable) log.info operating_mode[om]
    if(!operating_mode[om]) log.warn "$om is not defined"

    def flow_tile_large = "<div><table style='margin: auto'>"
    flow_tile_large += "<tr><td></td><td></td><td>" + formatEnergy(state.Production_W) + "</td><td></td><td></td></tr>"
    flow_tile_large += "<tr><td></td><td>" + (state.FlowConsumptionProduction == true ? "<img src=\"https://img.icons8.com/material-sharp/48/26e07f/left-down2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/material-outlined/48/4a90e2/sun--v1.png\"/></td><td>" + (state.FlowProductionBattery == true ? "<img src=\"https://img.icons8.com/material-outlined/48/26e07f/right-down2.png\"/>" : "") + "</td><td></td></tr>"
    flow_tile_large += "<tr><td>" + formatEnergy(state.Consumption_W) + "</td><td><img src='https://img.icons8.com/material-outlined/48/4a90e2/cottage.png'/></td><td>" + (state.FlowProductionGrid == true ? "<img src=\"https://img.icons8.com/material-rounded/48/26e07f/long-arrow-down.png\"/>" : "") + (state.FlowConsumptionBattery == true ? "<img src=\"https://img.icons8.com/material-rounded/48/26e07f/long-arrow-left.png\"/>" : "") + "</td><td><img src='https://img.icons8.com/ios-glyphs/48/4a90e2/battery--v1.png'/></td><td>" + formatEnergy(state.Pac_total_W) + "</td></tr>"
    flow_tile_large += "<tr><td></td><td>" + (state.FlowConsumptionGrid == true ? "<img src=\"https://img.icons8.com/material-outlined/48/fa314a/left-up2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/ios/48/4a90e2/transmission-tower.png\"/></td><td>" + ((state.FlowGridBattery == true && state.BatteryCharging == true) ? "<img src=\"https://img.icons8.com/material-outlined/48/fa314a/right-up2.png\"/>" : "") + ((state.FlowGridBattery == true && state.BatteryDischarging == true) ? "<img src=\"https://img.icons8.com/material-outlined/48/26e07f/down-left.png\"/>" : "") + "</td><td></td></tr>"
    flow_tile_large += "<tr><td></td><td></td><td>" + formatEnergy(state.GridFeedIn_W) + "</td><td></td><td></td></tr>"
    flow_tile_large += "</table></div>"

    sendEvent(name: "flow_tile_large", value: "${flow_tile_large}")

    def flow_tile_small = "<div><table style='margin: auto'>"
    flow_tile_small += "<tr><td>" + (state.FlowConsumptionProduction == true ? "<img src=\"https://img.icons8.com/material-sharp/24/26e07f/left-down2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/material-outlined/24/4a90e2/sun--v1.png\"/></td><td>" + (state.FlowProductionBattery == true ? "<img src=\"https://img.icons8.com/material-outlined/24/26e07f/right-down2.png\"/>" : "") + "</td></tr>"
    flow_tile_small += "<tr><td><img src='https://img.icons8.com/material-outlined/24/4a90e2/cottage.png'/></td><td>" + (state.FlowProductionGrid == true ? "<img src=\"https://img.icons8.com/material-rounded/24/26e07f/long-arrow-down.png\"/>" : "") + (state.FlowConsumptionBattery == true ? "<img src=\"https://img.icons8.com/material-rounded/24/26e07f/long-arrow-left.png\"/>" : "") + "</td><td><img src='https://img.icons8.com/ios-glyphs/24/4a90e2/battery--v1.png'/></td></tr>"
    flow_tile_small += "<tr><td>" + (state.FlowConsumptionGrid == true ? "<img src=\"https://img.icons8.com/material-outlined/24/fa314a/left-up2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/ios/30/4a90e2/transmission-tower.png\"/></td><td>" + ((state.FlowGridBattery == true && state.BatteryCharging == true) ? "<img src=\"https://img.icons8.com/material-outlined/24/fa314a/right-up2.png\"/>" : "") + ((state.FlowGridBattery == true && state.BatteryDischarging == true) ? "<img src=\"https://img.icons8.com/material-outlined/24/26e07f/down-left.png\"/>" : "") + "</td></tr>"
    flow_tile_small += "</table></div>"

    sendEvent(name: "flow_tile_small", value: "${flow_tile_small}")
}

def estimateCharge() {
    def FullChargeCapacity = state.FullChargeCapacity?.toBigDecimal() ?: 0
    def usoc = state.USOC?.toBigDecimal() ?: 0
    def pacTotal = state.Pac_total_W?.toBigDecimal() ?: 1  // Avoid division by zero

    def remaining_battery_power = (FullChargeCapacity * usoc / 100) - (FullChargeCapacity * FullChargeCapacity / 100)
    def amount_to_charge = FullChargeCapacity - (FullChargeCapacity * usoc / 100)

    def time_to_charge = 0
    def time_to_discharge = 0

    if (state.BatteryCharging) {
        time_to_charge = Math.round((amount_to_charge / pacTotal * -1 * 60))
    }
    if (state.BatteryDischarging) {
        time_to_discharge = Math.round((remaining_battery_power / pacTotal * 60))
    }

    sendEvent(name: "TimeToCharge", value: time_to_charge)
    sendEvent(name: "TimeToDischarge", value: time_to_discharge)
}


def setBackupBuffer(buffer) {
    def params = [
        uri: "http://${battery_ip_address}/api/v2/configurations",
        body: [
            EM_USOC: buffer
        ],
        contentType: "application/json",
        headers: [
            'Auth-Token': apiKey
        ]
    ]

    try {
        httpPut(params) { response ->
            if (response.status == 200) {
                log.info "Success: ${response.data}"
            } else {
                log.error "Failed with status: ${response.status}"
            }
        }
    } catch (Exception e) {
        log.error "Error: ${e.message}"
    }
}

def getFullChargeCapacity() {
    def params = [
        uri: "http://${battery_ip_address}/api/v2/latestdata",
        contentType: "application/json",
        headers: [
            'Auth-Token': apiKey
        ]
    ]
    
    try {
        httpGet(params) { response ->
            if (response.status == 200) {
                def respData = response.data
                
                if (respData?.FullChargeCapacity) {
                    state.FullChargeCapacity = respData.FullChargeCapacity
                    if (logEnable) log.info "Full Charge Capacity: ${state.FullChargeCapacity}"
                } else {
                    log.warn "FullChargeCapacity key not found in API response."
                }
            } else {
                log.error "Failed with status: ${response.status}"
            }
        }
    } catch (Exception e) {
        log.error "Error fetching charge capacity: ${e.message}"
    }
}

private formatEnergy(energy) {
    if (energy < 1000 && energy > -1000) return energy + " W"
    if (energy < 1000000 && energy > -1000000) return Math.round((double)(energy / 1000) * 100) / 100 + " kW"
    return Math.round((double)(energy / 1000 / 1000) * 100) / 100 + " MW"
}

private convertEnergy(energy) {
    if (energy < 0)
      energy = 0
    return energy
}
