metadata {
	definition(
name: "Sonnen Battery",
namespace: "Sonnen Battery",
author: "Ryan Lundell",
importUrl: "https://raw.githubusercontent.com/ke7lvb/sonnen_battery/main/sonnen_battery_driver.groovy",
	){
		capability "PowerSource"
		capability "PowerMeter"
		capability "Battery"
		capability "VoltageMeasurement"
        capability "Actuator"
		capability "Refresh"
		
		command "updateTiles"
		command "batteryChargeRate", [[name: "Set Battery Charge Rate", type: "NUMBER"]]
		command "batteryDischargeRate", [[name: "Set Battery Discharge Rate", type: "NUMBER"]]

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
		//attribute "GridFeedIn_W", "number"
		//attribute "IsSystemInstalled", "number"
		//attribute "OperatingMode", "number"
		attribute "Pac_total_W", "number"
		attribute "Production_W", "number"
		//attribute "RSOC", "number"
		//attribute "RemainingCapacity_W", "number"
		//attribute "SystemStatus", "string"
		//attribute "Timestamp", "string"
		//attribute "USOC", "number"
		//attribute "Uac", "number"
		//attribute "Ubat", "number"
		attribute "flow_tile_large", "string"
		attribute "flow_tile_small", "string"
		//attribute "connected", "string"
	}
	preferences {
		input name: "logEnable", type: "bool", title: "Enable logging", defaultValue: true, description: ""
		input name: "battery_ip_address", type: "string", title: "Sonnen battery LAN IP", description: "example: 192.168.0.2", required: true
		input("refresh_interval", "enum", title: "How often to refresh the battery data", options: [
0:"Do NOT update",
30:"30 seconds",
1:"1 Minute",
5:"5 Minutes",
10:"10 Minutes",
15:"15 Minutes",
20:"20 Minutes",
45:"45 Minutes",
		], required: true, defaultValue:"1")
	}
}

def version(){ return "1.1.3" }

def installed(){
	if(logEnable) log.info "Driver installed"
	
	state.version = version()
}

def uninstalled() {
	unschedule(refresh)
	if(logEnable) log.info "Driver uninstalled"
}

def updated(){
	unschedule(refresh)
	if(logEnable) log.info "Settings updated"
	if( settings.refresh_interval != "0"){
		refresh()
		if(settings.refresh_interval == "30"){
			schedule("15/${settings.refresh_interval} * * * * ? *", refresh)
		}else{
			schedule("15 */${settings.refresh_interval} * ? * *", refresh)
		}
	}
	state.version = version()
}

def refresh() {
	int count = 0;
	int maxTries = 3;
	while(count < maxTries){  
		def host = "http://"+battery_ip_address+":8080"
		def command = "/api/v1/status"
		try {
			httpGet([uri: "${host}${command}",
                timeout: 30
			]) {
				resp -> def respData = resp.data
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
				state.IsSystemInstalled = respData.IsSystemInstalled
				state.OperatingMode = respData.OperatingMode
				state.Pac_total_W = respData.Pac_total_W
                sendEvent(name: "Pac_total_W", value: state.Pac_total_W)
				state.Production_W = respData.Production_W
                sendEvent(name: "Production_W", value: state.Production_W)
				state.RSOC = respData.RSOC
                state.RemainingCapacity_W = respData.RemainingCapacity_W
				state.SystemStatus = respData.SystemStatus
				state.Timestamp = respData.Timestamp
				state.USOC = respData.USOC
				state.Uac = respData.Uac
				state.Ubat = respData.Ubat
				state.battery = respData.USOC
				sendEvent(name: "battery", value: state.battery)
				state.power = (respData.Production_W - respData.Consumption_W)
				sendEvent(name: "power", value: state.power)
				state.voltage = respData.Ubat
				sendEvent(name: "voltage", value: state.voltage)
				state.frequency = respData.Fac
				sendEvent(name: "frequency", value: state.frequency)
				state.powerSource =  (respData.FlowConsumptionBattery == true ? "battery" : "mains")
                sendEvent(name: "powerSource", value: state.powerSource)
				state.connected = true
				if(logEnable) log.info respData
			}
			count = maxTries
			
		} catch (e) {
			++count
			if(logEnable) log.warn "$count attempt to connect failed: $e"
			if (count >= maxTries) {
				if(logEnable) log.error "Max retries exceeded"
				state.connected = false
			}
		}
	}
	updateTiles()
}

def updateTiles() {

	def flow_tile_large = "<div><table style='margin: auto'>"
	flow_tile_large += "<tr><td></td><td></td><td>" + formatEnergy(state.Production_W) + "</td><td></td><td></td></tr>"
	flow_tile_large += "<tr><td></td><td>" + (state.FlowConsumptionProduction == true ? "<img src=\"https://img.icons8.com/material-sharp/48/26e07f/left-down2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/material-outlined/48/4a90e2/sun--v1.png\"/></td><td>" + (state.FlowProductionBattery == true ? "<img src=\"https://img.icons8.com/material-outlined/48/26e07f/right-down2.png\"/>" : "") + "</td><td></td></tr>"
	flow_tile_large += "<tr><td>" + formatEnergy(state.Consumption_W) + "</td><td><img src='https://img.icons8.com/material-outlined/48/4a90e2/cottage.png'/></td><td>" + (state.FlowProductionGrid == true ? "<img src=\"https://img.icons8.com/material-rounded/48/26e07f/long-arrow-down.png\"/>" : "") + (state.FlowConsumptionBattery == true ? "<img src=\"https://img.icons8.com/material-rounded/48/26e07f/long-arrow-left.png\"/>" : "") + "</td><td><img src='https://img.icons8.com/ios-glyphs/48/4a90e2/battery--v1.png'/></td><td>" + formatEnergy(state.Pac_total_W) + "</td></tr>"
	flow_tile_large += "<tr><td></td><td>" + (state.FlowConsumptionGrid == true ? "<img src=\"https://img.icons8.com/material-outlined/48/fa314a/left-up2.png\"/>" : "" ) + "</td><td><img src=\"https://img.icons8.com/ios/48/4a90e2/transmission-tower.png\"/></td><td>" + ((state.FlowGridBattery == true && state.BatteryCharging == true) ? "<img src=\"https://img.icons8.com/material-outlined/48/fa314a/right-up2.png\"/>" : "") + ((state.FlowGridBattery == true && state.BatteryDischarging == true) ? "<img src=\"https://img.icons8.com/material-outlined/48/26e07f/down-left.png\"/>" : "") + "</td><td></td></tr>"
	flow_tile_large += "<tr><td></td><td></td><td>" + formatEnergy(state.GridFeedIn_W) + "</td><td></td><td></td></tr>"
	flow_tile_large += "</table></div>"

	sendEvent(name: "flow_tile_large", value: flow_tile_large)

	def flow_tile_small = "<div><table style='margin: auto'>"
	flow_tile_small += "<tr><td>" + (state.FlowConsumptionProduction == true ? "<img src=\"https://img.icons8.com/material-sharp/24/26e07f/left-down2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/material-outlined/24/4a90e2/sun--v1.png\"/></td><td>" + (state.FlowProductionBattery == true ? "<img src=\"https://img.icons8.com/material-outlined/24/26e07f/right-down2.png\"/>" : "") + "</td></tr>"
	flow_tile_small += "<tr><td><img src='https://img.icons8.com/material-outlined/24/4a90e2/cottage.png'/></td><td>" + (state.FlowProductionGrid == true ? "<img src=\"https://img.icons8.com/material-rounded/24/26e07f/long-arrow-down.png\"/>" : "") + (state.FlowConsumptionBattery == true ? "<img src=\"https://img.icons8.com/material-rounded/24/26e07f/long-arrow-left.png\"/>" : "") + "</td><td><img src='https://img.icons8.com/ios-glyphs/24/4a90e2/battery--v1.png'/></td></tr>"
	flow_tile_small += "<tr><td>" + (state.FlowConsumptionGrid == true ? "<img src=\"https://img.icons8.com/material-outlined/24/fa314a/left-up2.png\"/>" : "" ) + "</td><td><img src=\"https://img.icons8.com/ios/30/4a90e2/transmission-tower.png\"/></td><td>" + ((state.FlowGridBattery == true && state.BatteryCharging == true) ? "<img src=\"https://img.icons8.com/material-outlined/24/fa314a/right-up2.png\"/>" : "") + ((state.FlowGridBattery == true && state.BatteryDischarging == true) ? "<img src=\"https://img.icons8.com/material-outlined/24/26e07f/down-left.png\"/>" : "") +  "</td></tr>"
	flow_tile_small += "</table></div>"

	sendEvent(name: "flow_tile_small", value: flow_tile_small)
}

def batteryChargeRate(rate) {
	def host = "http://"+battery_ip_address+":8080"
	def command = "/api/v1/setpoint/charge/"
    httpGet([uri: "${host}${command}${rate}"]) {
        resp -> def respData = resp.data
        if(logEnable) log.info "${host}${command}${rate}"
        if(logEnable) log.info respData
    }
    pauseExecution(8000)
    refresh()
}


def batteryDischargeRate(rate) {
	def host = "http://"+battery_ip_address+":8080"
	def command = "/api/v1/setpoint/discharge/"
    httpGet([uri: "${host}${command}${rate}"]) {
        resp -> def respData = resp.data
        if(logEnable) log.info "${host}${command}${rate}"
        if(logEnable) log.info respData
    }
    pauseExecution(8000)
    refresh()
}

private formatEnergy(energy) {
	if (energy < 1000 && energy > -1000) return energy + " W"
	if (energy < 1000000 && energy > -1000000) return Math.round((double)(energy / 1000) * 100) / 100 + " kW"
	return Math.round((double)(energy / 1000 / 1000) * 100) / 100 + " MW"
}
