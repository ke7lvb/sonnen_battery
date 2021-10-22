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
    capability "EnergyMeter"
    capability "VoltageMeasurement"
    capability "Refresh"
    command "updateTiles"

    attribute "BackupBuffer", "number"
    attribute "BatteryCharging", "string"
    attribute "BatteryDischarging", "string"
    attribute "Consumption_Avg", "number"
    attribute "Consumption_W", "number"
    attribute "Fac", "number"
    attribute "FlowConsumptionBattery", "string"
    attribute "FlowConsumptionGrid", "string"
    attribute "FlowConsumptionProduction", "string"
    attribute "FlowGridBattery", "string"
    attribute "FlowProductionBattery", "string"
    attribute "FlowProductionGrid", "string"
    attribute "GridFeedIn_W", "number"
    attribute "IsSystemInstalled", "number"
    attribute "OperatingMode", "number"
    attribute "Pac_total_W", "number"
    attribute "Production_W", "number"
    attribute "RSOC", "number"
    attribute "RemainingCapacity_W", "number"
    attribute "SystemStatus", "string"
    attribute "Timestamp", "string"
    attribute "USOC", "number"
    attribute "Uac", "number"
    attribute "Ubat", "number"
    attribute "flow_tile_large", "string"
    attribute "flow_tile_small", "string"
    attribute "connected", "string"
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

def version(){ return "1.0.5" }

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
      schedule("0/${settings.refresh_interval} * * * * ? *", refresh)
    }else{
      schedule("0 */${settings.refresh_interval} * ? * *", refresh)
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
		  sendEvent(name: "BackupBuffer", value: respData.BackupBuffer)
		  sendEvent(name: "BatteryCharging", value: respData.BatteryCharging)
		  sendEvent(name: "BatteryDischarging", value: respData.BatteryDischarging)
		  sendEvent(name: "Consumption_Avg", value: (respData.Consumption_Avg))
		  sendEvent(name: "Consumption_W", value: (respData.Consumption_W))
		  sendEvent(name: "Fac", value: respData.Fac)
		  sendEvent(name: "FlowConsumptionBattery", value: respData.FlowConsumptionBattery)
		  sendEvent(name: "FlowConsumptionGrid", value: respData.FlowConsumptionGrid)
		  sendEvent(name: "FlowConsumptionProduction", value: respData.FlowConsumptionProduction)
		  sendEvent(name: "FlowGridBattery", value: respData.FlowGridBattery)
		  sendEvent(name: "FlowProductionBattery", value: respData.FlowProductionBattery)
		  sendEvent(name: "FlowProductionGrid", value: respData.FlowProductionGrid)
		  sendEvent(name: "GridFeedIn_W", value: (respData.GridFeedIn_W))
		  sendEvent(name: "IsSystemInstalled", value: respData.IsSystemInstalled)
		  sendEvent(name: "OperatingMode", value: respData.OperatingMode)
		  sendEvent(name: "Pac_total_W", value: (respData.Pac_total_W))
		  sendEvent(name: "Production_W", value: respData.Production_W)
		  sendEvent(name: "RSOC", value: respData.RSOC)
		  sendEvent(name: "RemainingCapacity_W", value: (respData.RemainingCapacity_W))
		  sendEvent(name: "SystemStatus", value: respData.SystemStatus)
		  sendEvent(name: "Timestamp", value: respData.Timestamp)
		  sendEvent(name: "USOC", value: respData.USOC)
		  sendEvent(name: "Uac", value: respData.Uac)
		  sendEvent(name: "Ubat", value: respData.Ubat)
		  sendEvent(name: "battery", value: respData.USOC)
		  sendEvent(name: "power", value: respData.Production_W - respData.Consumption_W)
		  sendEvent(name: "voltage", value: respData.Uac)
		  sendEvent(name: "frequency", value: respData.Fac)
		  sendEvent(name: "energy", value: (respData.Production_W - respData.Consumption_W))
		  sendEvent(name: "powerSource", value: (respData.FlowConsumptionBattery == true) ? "battery" : "mains")
		  sendEvent(name: "connected", value: "true")
		  if(logEnable) log.info respData
		}
		count = maxTries
		
	  } catch (e) {
      ++count
		if(logEnable) log.warn "$count attempt to connect failed: $e"
	    if (count >= maxTries) {
		    if(logEnable) log.error "Max retries exceeded"
		    sendEvent(name: "connected", value: "false")
		  }
	  }
	}
  pauseExecution(500)
  updateTiles()
}

def updateTiles() {
  
  def flow_tile_large = "<div><table style='margin: auto'>"
  flow_tile_large += "<tr><td></td><td></td><td>" + formatEnergy(device.currentValue("Production_W")) + "</td><td></td><td></td></tr>"
  flow_tile_large += "<tr><td></td><td>" + (device.currentValue("FlowConsumptionProduction") == "true" ? "<img src=\"https://img.icons8.com/material-sharp/48/26e07f/left-down2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/material-outlined/48/4a90e2/sun--v1.png\"/></td><td>" + (device.currentValue("FlowProductionBattery") == "true" ? "<img src=\"https://img.icons8.com/material-outlined/48/26e07f/right-down2.png\"/>" : "") + "</td><td></td></tr>"
  flow_tile_large += "<tr><td>" + formatEnergy(device.currentValue("Consumption_W")) + "</td><td><img src='https://img.icons8.com/material-outlined/48/4a90e2/cottage.png'/></td><td>" + (device.currentValue("FlowProductionGrid") == "true" ? "<img src=\"https://img.icons8.com/material-rounded/48/26e07f/long-arrow-down.png\"/>" : "") + (device.currentValue("FlowConsumptionBattery") == "true" ? "<img src=\"https://img.icons8.com/material-rounded/48/26e07f/long-arrow-left.png\"/>" : "") + "</td><td><img src='https://img.icons8.com/ios-glyphs/48/4a90e2/battery--v1.png'/></td><td>" + formatEnergy(device.currentValue("Pac_total_W")) + "</td></tr>"
  flow_tile_large += "<tr><td></td><td>" + (device.currentValue("FlowConsumptionGrid") == "true" ? "<img src=\"https://img.icons8.com/material-outlined/48/fa314a/left-up2.png\"/>" : "" ) + "</td><td><img src=\"https://img.icons8.com/ios/48/4a90e2/transmission-tower.png\"/></td><td>" + ((device.currentValue("FlowGridBattery") == "true" && device.currentValue("BatteryCharging") == "true") ? "<img src=\"https://img.icons8.com/material-outlined/48/fa314a/right-up2.png\"/>" : "") + ((device.currentValue("FlowGridBattery") == "true" && device.currentValue("BatteryDischarging") == "true") ? "<img src=\"https://img.icons8.com/material-outlined/48/26e07f/down-left.png\"/>" : "") + "</td><td></td></tr>"
  flow_tile_large += "<tr><td></td><td></td><td>" + formatEnergy(device.currentValue("GridFeedIn_W")) + "</td><td></td><td></td></tr>"
  flow_tile_large += "</table></div>"

  sendEvent(name: "flow_tile_large", value: flow_tile_large)
  
  def flow_tile_small = "<div><table style='margin: auto'>"
  flow_tile_small += "<tr><td>" + (device.currentValue("FlowConsumptionProduction") == "true" ? "<img src=\"https://img.icons8.com/material-sharp/24/26e07f/left-down2.png\"/>" : "") + "</td><td><img src=\"https://img.icons8.com/material-outlined/24/4a90e2/sun--v1.png\"/></td><td>" + (device.currentValue("FlowProductionBattery") == "true" ? "<img src=\"https://img.icons8.com/material-outlined/24/26e07f/right-down2.png\"/>" : "") + "</td></tr>"
  flow_tile_small += "<tr><td><img src='https://img.icons8.com/material-outlined/24/4a90e2/cottage.png'/></td><td>" + (device.currentValue("FlowProductionGrid") == "true" ? "<img src=\"https://img.icons8.com/material-rounded/24/26e07f/long-arrow-down.png\"/>" : "") + (device.currentValue("FlowConsumptionBattery") == "true" ? "<img src=\"https://img.icons8.com/material-rounded/24/26e07f/long-arrow-left.png\"/>" : "") + "</td><td><img src='https://img.icons8.com/ios-glyphs/24/4a90e2/battery--v1.png'/></td></tr>"
  flow_tile_small += "<tr><td>" + (device.currentValue("FlowConsumptionGrid") == "true" ? "<img src=\"https://img.icons8.com/material-outlined/24/fa314a/left-up2.png\"/>" : "" ) + "</td><td><img src=\"https://img.icons8.com/ios/30/4a90e2/transmission-tower.png\"/></td><td>" + ((device.currentValue("FlowGridBattery") == "true" && device.currentValue("BatteryCharging") == "true") ? "<img src=\"https://img.icons8.com/material-outlined/24/fa314a/right-up2.png\"/>" : "") + ((device.currentValue("FlowGridBattery") == "true" && device.currentValue("BatteryDischarging") == "true") ? "<img src=\"https://img.icons8.com/material-outlined/24/26e07f/down-left.png\"/>" : "") +  "</td></tr>"
  flow_tile_small += "</table></div>"

  sendEvent(name: "flow_tile_small", value: flow_tile_small)
}

private formatEnergy(energy) {
  if (energy < 1000 && energy > -1000) return energy + " W"
  if (energy < 1000000 && energy > -1000000) return Math.round((double)(energy / 1000) * 100) / 100 + " kW"
  return Math.round((double)(energy / 1000 / 1000) * 100) / 100 + " MW"
}
