/**
 *
 *  Laundry Status - Aeon Smart Energy Switch 6 (gen5)
 *
 *  Interprets the current wattage into as status of idle, running, or finished
 *
 *  heavily based on jbisson's Aeon Smart Energy Switch 6 dh
 *  https://github.com/jbisson/SmartThingsPublic-/blob/master/devicetypes/jbisson/aeon-labs-smart-switch-6-gen5.src/aeon-labs-smart-switch-6-gen5.groovy
 *
 *
 */

metadata {
    definition(name: "Laundry Status", namespace: "augoisms", author: "Justin Walker") {
        capability "Polling"
        capability "Power Meter"
        capability "Energy Meter"
        capability "Refresh"
        capability "Switch Level"
        capability "Sensor"
        capability "Actuator"
        capability "Configuration"

        command "reset"
        command "getDeviceInfo"
        command "resetFinished"

        attribute "deviceMode", "String"
        attribute "status", "enum", ["idle", "running", "finished"]
        attribute "wasRunning", "enum", ["true", "false"]

    }

    tiles {
        standardTile("status", "status", width: 2, height: 2, canChangeIcon: true, decoration: "flat") {
			state "idle", label: 'Idle', icon: "st.Appliances.appliances1", backgroundColor: "#ffffff"
            state "running", label: 'Running', icon: "st.Appliances.appliances1", backgroundColor: "#00a0dc"
            state "finished", label: 'Finished', action: "resetFinished", icon: "st.Appliances.appliances1", backgroundColor: "#e86d13"
		}
		valueTile("power", "device.power") {
			state "default", label:'${currentValue} W'
		}
        valueTile("amperage", "device.amperage") {
            state "default", label: '${currentValue} A'
        }
        valueTile("voltage", "device.voltage") {
            state "default", label: '${currentValue} v'
        }
        standardTile("refresh", "device.switch", decoration: "flat") {
            state "default", label: "", action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        main(["status"])
        details(["status", "power", "amperage", "voltage", "refresh"])
    }
}

preferences {
    
    input title: "", description: "State related options", type: "paragraph", element: "paragraph"
    input name: "wattThreshold", type: "number", title: "Minimum watt value to trigger state\n", defaultValue: "10", displayDuringSetup: true
    input name: "minTimeOff", type: "number", title: "Minimum time (min) watts need to stay below the threshold\n", defaultValue: "2", displayDuringSetup: true

    input title: "", description: "Device Options", type: "paragraph", element: "paragraph"
    input name: "refreshInterval", type: "number", title: "Refresh interval \n\nSet the refresh time interval (seconds) between each reports.\n", defaultValue: "300", displayDuringSetup: true
    input name: "forceStateChangeOnReport", type: "bool", title: "Force state change when receiving a report ? If true, you'll always get notification even if report data doesn't change.\n", defaultValue: "false", displayDuringSetup: true
    input name: "secureInclusionOverride", type: "bool", title: "Is this device in secure inclusive mode?\n", defaultValue: "false", displayDuringSetup: true

    input name: "onlySendReportIfValueChange", type: "bool", title: "Only send report if value change (either in terms of wattage or a %)\n", defaultValue: "true", displayDuringSetup: true
    input title: "", description: "The next two parameters are only working if the 'only send report' is set to true.", type: "paragraph", element: "paragraph", displayDuringSetup: true

    input name: "minimumChangeWatts", type: "number", title: "Minimum change in wattage for a report to be sent (0 - 100).\n", defaultValue: "2", range: "0..100", displayDuringSetup: true
    input name: "minimumChangePercent", type: "number", title: "Minimum change in percentage for a report to be sent (0 - 60000)\n", defaultValue: "5", range: "0..60000", displayDuringSetup: true

    input name: "includeWattInReport", type: "bool", title: "Include energy meter (W) in report?\n", defaultValue: "true", displayDuringSetup: true
    input name: "includeVoltageInReport", type: "bool", title: "Include voltage (V) in report?\n", defaultValue: "true", displayDuringSetup: true
    input name: "includeCurrentInReport", type: "bool", title: "Include current (A) in report?\n", defaultValue: "true", displayDuringSetup: true

    input title: "", description: "Logging", type: "paragraph", element: "paragraph"
    input name: "isLogLevelTrace", type: "bool", title: "Show trace log level ?\n", defaultValue: "false", displayDuringSetup: true
    input name: "isLogLevelDebug", type: "bool", title: "Show debug log level ?\n", defaultValue: "true", displayDuringSetup: true
}

/*******************************************************************************
 * 	Z-WAVE PARSE / EVENTS                                                      *
 ******************************************************************************/

/**
 *  parse - Called when messages from a device are received from the hub
 *
 *  The parse method is responsible for interpreting those messages and returning Event definitions.
 *
 *  String	description		The message from the device
 */
def parse(String description) {
    def result = null
    logTrace "parse: '$description'"

    if (description != "updated") {
        def cmd = zwave.parse(description, [0x98: 1, 0x20: 1, 0x26: 3, 0x70: 1, 0x32: 3])
        logTrace "cmd: '$cmd'"

        if (cmd) {
            result = zwaveEvent(cmd)
            //log.debug("'$description' parsed to $result $result?.name")
        } else {
            log.error "Couldn't zwave.parse '$description'"
        }
    }


    result
}

/**
 *  COMMAND_CLASS_SECURITY (0x98)
 *
 *
 */
def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x26: 3, 0x70: 1, 0x32: 3])
    logTrace "secure cmd: '$cmd'"
    state.deviceInfo['secureInclusion'] = true;

    // can specify command class versions here like in zwave.parse
    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand)
    } else {
        log.error "Unable to extract encapsulated cmd from $cmd"
    }
}

/**
 *  COMMAND_CLASS_SECURITY (0x98)
 *
 *
 */
def zwaveEvent(hubitat.zwave.commands.securityv1.NetworkKeyVerify cmd) {
    log.debug "NetworkKeyVerify with cmd: $cmd (node is securely included)"

    //after device securely joined the network, call configure() to config device
    state.deviceInfo['secureInclusion'] = true;
    updateDeviceInfo()
}

/**
 *  COMMAND_CLASS_SWITCH_BINARY (0x25)
 *
 *  Short	value	0xFF for on, 0x00 for off
 */
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinarySet cmd) {
    createEvent(name: "switch", value: cmd.switchValue ? "on" : "off")

    //return createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

/**
 *  COMMAND_CLASS_SWITCH_BINARY (0x25)
 *
 *  Short	value	0xFF for on, 0x00 for off
 */
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    createEvent(name: "switch", value: cmd.value ? "on" : "off", displayed: false, isStateChange: true)
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {

}

/**
 *  COMMAND_CLASS_BASIC (0x20)
 *  This command is being ignored in secure inclusion mode.
 *
 *  Short	value	0xFF for on, 0x00 for off
 */
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    return createEvent(name: "switch", value: cmd.value ? "on" : "off", displayed: false)
}

/**
 *  COMMAND_CLASS_BASIC (0x20)
 *
 *  This command is being ignored in secure inclusion mode.
 *  Short	value	0xFF for on, 0x00 for off
 */
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    return createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

/**
 *  COMMAND_CLASS_SWITCH_MULTILEVEL (0x26)
 *
 *  Short	value
 */
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {

}

/**
 *  COMMAND_CLASS_METER (0x32)
 *
 *  Integer	deltaTime		    Time in seconds since last report
 *  Short	meterType		    Unknown = 0, Electric = 1, Gas = 2, Water = 3
 *  List<Short>	meterValue		    Meter value as an array of bytes
 *  Double	scaledMeterValue	    Meter value as a double
 *  List<Short>	previousMeterValue	    Previous meter value as an array of bytes
 *  Double	scaledPreviousMeterValue    Previous meter value as a double
 *  Short	size			    The size of the array for the meterValue and previousMeterValue
 *  Short	scale			    The scale of the values: "kWh"=0, "kVAh"=1, "Watts"=2, "pulses"=3, "Volts"=4, "Amps"=5, "Power Factor"=6, "Unknown"=7
 *  Short	precision		    The decimal precision of the values
 *  Short	rateType		    ???
 *  Boolean	scale2			    ???
 */
def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
    if (cmd.meterType == 1) {
        def eventList = []
        if (cmd.scale == 0) {
            logDebug " got kwh $cmd.scaledMeterValue"

            eventList.push(internalCreateEvent([name: "energy", value: cmd.scaledMeterValue, unit: "kWh"]));

        } else if (cmd.scale == 1) {
            logDebug " got kVAh $cmd.scaledMeterValue"
            eventList.push(internalCreateEvent([name: "energy", value: cmd.scaledMeterValue, unit: "kVAh"]));
        } else if (cmd.scale == 2) {
            logDebug " got wattage $cmd.scaledMeterValue"
            eventList.push(internalCreateEvent([name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W"]));
            
            // update status based on current watts
            // ------------------------------------
            eventList.push(internalCreateEvent([name: "status", value: parseWattsForStatus(Math.round(cmd.scaledMeterValue))]));
            
        } else if (cmd.scale == 4) { // Volts
            logDebug " got voltage $cmd.scaledMeterValue"
            eventList.push(internalCreateEvent([name: "voltage", value: Math.round(cmd.scaledMeterValue), unit: "V"]));
        } else if (cmd.scale == 5) { //amps scale 5 is amps even though not documented
            logDebug " got amperage = $cmd.scaledMeterValue"
            eventList.push(internalCreateEvent([name: "amperage", value: cmd.scaledMeterValue, unit: "A"]));
        } else {
            eventList.push(internalCreateEvent([name: "electric", value: cmd.scaledMeterValue, unit: ["pulses", "V", "A", "R/Z", ""][cmd.scale - 3]]));
        }

        return eventList

    }
}

def parseWattsForStatus(watts) {
	log.debug "parseWattsForStatus()"
	// get the current status
	def currentStatus = device.currentValue("status") ?: "";
    // check if running to save for later
    if(currentStatus == "running") {
        state.wasRunning = true;
    }
    log.debug "parseWattsForStatus() > wasRunning: ${state.wasRunning}";
    
	log.debug "parseWattsForStatus() > checking watt value";
    log.debug "parseWattsForStatus() > watts: " + watts;
    log.debug "parseWattsForStatus() > currentStatus: ${currentStatus}";
    
    def isIdle = watts < wattThreshold;
    log.debug "parseWattsForStatus() > isIdle: " + isIdle;
    
    // check if we're *really* idle
    if(isIdle && state.timeLastIdle) {
    	// we have been idle before
        // check if we have been idle long enough
        def minOffTimeMillis = minTimeOff * 60 * 1000; // one minute, move this to a preference
        log.debug "parseWattsForStatus() > minOffTimeMillis: " + minOffTimeMillis;
        log.debug "parseWattsForStatus() > now(): " + now();
        log.debug "parseWattsForStatus() > state.timeLastIdle: " + state.timeLastIdle;
        def lastLowDelta = (now() - state.timeLastIdle)
        log.debug "parseWattsForStatus() > lastLowDelta: " + lastLowDelta;
        if (lastLowDelta >= minOffTimeMillis) { 
        	// Power has been low for long enough
            // let isIdle remain true
            log.debug "parseWattsForStatus() > Min off time has been met, let's go idle"
            isIdle = true;
        } else { 
        	// Has a low time but isn't done yet, re-schedule another check for the future
            def reSchedTime = (minOffTime - lastLowDelta) / 1000
            log.trace "parseWattsForStatus() > Scheduling a refresh() in ${reSchedTime} seconds"
            runIn(reSchedTime + 2, refresh);j
            isIdle = false;
        }
    } else if(isIdle) {
    	// we're idle but we don't have a previous record
        // save the current time and ignore this report
        // return last known/current status
        log.debug "parseWattsForStatus() > No previous idle record, ignoring for now"
        state.timeLastIdle = now();
        isIdle = false;
    } else {
    	// we're not idle, zero out lastTimeIdle
        state.timeLastIdle = false;
    }
    
    log.debug "parseWattsForStatus() > isIdle after checks: " + isIdle;
    
    def finalState = "";
    if(isIdle && state.wasRunning) {
    	finalState = "finished";
    } else if (isIdle) {
    	finalState = "idle";
    } else {
    	finalState = "running";
    }
    
    log.debug "parseWattsForStatus() > finalState: " + finalState;
    
    return finalState;
}

def resetFinished() {    
	state.wasRunning = false;
    log.debug "resetFinished() > wasRunning: ${state.wasRunning}";
    refresh();
}

/**
 *  COMMAND_CLASS_CONFIGURATION (0x70)
 *
 *  List<Short>	configurationValue
 *  Short	parameterNumber
 *  Short	size
 */
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logTrace "received ConfigurationReport for " + cmd.parameterNumber + " (hex:" + Integer.toHexString(cmd.parameterNumber) + ") cmd: " + cmd
    switch (cmd.parameterNumber) {
        case 0x51:
            logTrace "received device mode event"
            if (cmd.configurationValue[0] == 0) {
                return createEvent(name: "deviceMode", value: "energy", displayed: true)
            } else if (cmd.configurationValue[0] == 1) {
                return createEvent(name: "deviceMode", value: "momentary", displayed: true)
            } else if (cmd.configurationValue[0] == 2) {
                return createEvent(name: "deviceMode", value: "nightLight", displayed: true)
            }
            break;
        case 0x54:
            logTrace "received brightness level event"
            return createEvent(name: "level", value: cmd.configurationValue[0], displayed: true)
            break;
    }
}

/**
 *  COMMAND_CLASS_HAIL (0x82)
 *
 */
def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
    logDebug "Switch button was pressed"
    return createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed")
}

/**
 *  COMMAND_CLASS_VERSION (0x86)
 *
 *  Short	applicationSubVersion
 *  Short	applicationVersion
 *  Short	zWaveLibraryType
 *  Short	zWaveProtocolSubVersion
 *  Short	zWaveProtocolVersion
 */
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    state.deviceInfo['applicationVersion'] = "${cmd.applicationVersion}"
    state.deviceInfo['applicationSubVersion'] = "${cmd.applicationSubVersion}"
    state.deviceInfo['zWaveLibraryType'] = "${cmd.zWaveLibraryType}"
    state.deviceInfo['zWaveProtocolVersion'] = "${cmd.zWaveProtocolVersion}"
    state.deviceInfo['zWaveProtocolSubVersion'] = "${cmd.zWaveProtocolSubVersion}"

    return updateDeviceInfo()
}

/**
 *  COMMAND_CLASS_MANUFACTURER_SPECIFIC (0x72)
 *
 *  Integer	manufacturerId
 *  Integer	productId
 *  Integer	productTypeId
 *
 */
def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    state.deviceInfo['manufacturerId'] = "${cmd.manufacturerId}"
    state.deviceInfo['manufacturerName'] = "${cmd.manufacturerName}"
    state.deviceInfo['productId'] = "${cmd.productId}"
    state.deviceInfo['productTypeId'] = "${cmd.productTypeId}"

    return updateDeviceInfo()
}

/**
 *  COMMAND_CLASS_MANUFACTURER_SPECIFIC (0x72)
 *
 * List<Short>	deviceIdData
 * Short	deviceIdDataFormat
 * Short	deviceIdDataLengthIndicator
 * Short	deviceIdType
 *
 */
def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    logTrace "deviceIdData:  	          ${cmd.deviceIdData}"
    logTrace "deviceIdDataFormat:         ${cmd.deviceIdDataFormat}"
    logTrace "deviceIdDataLengthIndicator:${cmd.deviceIdDataLengthIndicator}"
    logTrace "deviceIdType:               ${cmd.deviceIdType}"

    return updateDeviceInfo()
}

/**
 *  COMMAND_CLASS_FIRMWARE_UPDATE_MD_V2 (0x7a)
 *
 * Integer	checksum
 * Integer	firmwareId
 * Integer	manufacturerId
 *
 */
def zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    state.deviceInfo['checksum'] = "${cmd.checksum}"
    state.deviceInfo['firmwareId'] = "${cmd.firmwareId}"

    return updateDeviceInfo()
}

/*******************************************************************************
 * 	CAPABILITITES                                                              *
 ******************************************************************************/

/**
 *  configure - Configures the parameters of the device
 *
 *  Required for the "Configuration" capability
 */
def configure() {
    logDebug "configure()"

    updateDeviceInfo()

    def switchAllMode = hubitat.zwave.commands.switchallv1.SwitchAllSet.MODE_EXCLUDED_FROM_THE_ALL_ON_ALL_OFF_FUNCTIONALITY // disabled

    logTrace "forceStateChangeOnReport value: " + forceStateChangeOnReport
    logTrace "switchAll value: " + switchAll
    
    def includeCurrentUsageInReport = false

    def reportGroup;
    reportGroup = ("$includeVoltageInReport" == "true" ? 1 : 0)
    reportGroup += ("$includeCurrentInReport" == "true" ? 2 : 0)
    reportGroup += ("$includeWattInReport" == "true" ? 4 : 0)
    reportGroup += ("$includeCurrentUsageInReport" == "true" ? 8 : 0)

    log.trace "setting configuration refresh interval: " + new BigInteger("$refreshInterval")


    delayBetween([
            formatCommand(zwave.switchAllV1.switchAllSet(mode: switchAllMode)),
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x50, size: 1, scaledConfigurationValue: 0)),    //Enable to send notifications to associated devices when load changes (0=nothing, 1=hail CC, 2=basic CC report)
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x5A, size: 1, scaledConfigurationValue: ("$onlySendReportIfValueChange" == "true" ? 1 : 0))),    //Enables parameter 0x5B and 0x5C (0=disabled, 1=enabled)
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x5B, size: 2, scaledConfigurationValue: new BigInteger("$minimumChangeWatts"))),    //Minimum change in wattage for a REPORT to be sent (Valid values 0 - 60000)
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x5C, size: 1, scaledConfigurationValue: "$minimumChangePercent")),    //Minimum change in percentage for a REPORT to be sent (Valid values 0 - 100)

            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x65, size: 4, scaledConfigurationValue: reportGroup)),    //Which reports need to send in Report group 1
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x66, size: 4, scaledConfigurationValue: 0)),    //Which reports need to send in Report group 2
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x67, size: 4, scaledConfigurationValue: 0)),    //Which reports need to send in Report group 3

            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x6F, size: 4, scaledConfigurationValue: new BigInteger("$refreshInterval"))),    // change reporting time
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x70, size: 4, scaledConfigurationValue: new BigInteger(0xFFFFF))),
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x71, size: 4, scaledConfigurationValue: new BigInteger(0xFFFFF))),

            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x3, size: 1, scaledConfigurationValue: 0)),      // Current Overload Protection.
            
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x54, size: 3, configurationValue: [0, 0, 0])), // brightness level 0
            formatCommand(zwave.configurationV1.configurationSet(parameterNumber: 0x51, size: 1, scaledConfigurationValue: 0)) // set to energy mode
    ], 200)
}



/**
 *  poll - Polls the device
 *
 *  Required for the "Polling" capability
 */
def poll() {
    logTrace "poll()"

    delayBetween([
            formatCommand(zwave.switchBinaryV1.switchBinaryGet()),
            formatCommand(zwave.meterV3.meterGet(scale: 0)), // energy kWh
            formatCommand(zwave.meterV3.meterGet(scale: 1)), // energy kVAh
            formatCommand(zwave.meterV3.meterGet(scale: 2)), // watts
            formatCommand(zwave.meterV3.meterGet(scale: 4)), // volts
            formatCommand(zwave.meterV3.meterGet(scale: 5)), // amps
    ], 200)
}

/**
 *  refresh - Refreshed values from the device
 *
 *  Required for the "Refresh" capability
 */
def refresh() {
    logDebug "refresh()"
    updateDeviceInfo()

    // why are these being zeroed out?
    // shouldn't we just keep the previous values until new ones come in?
    /*
    sendEvent(name: "power", value: "0", displayed: true, unit: "W")
    sendEvent(name: "energy", value: "0", displayed: true, unit: "kWh")
    sendEvent(name: "amperage", value: "0", displayed: true, unit: "A")
    sendEvent(name: "voltage", value: "0", displayed: true, unit: "V")

    sendEvent(name: "currentEnergyCostHour", value: "0", displayed: true)
    sendEvent(name: "currentEnergyCostWeek", value: "0", displayed: true)
    sendEvent(name: "currentEnergyCostMonth", value: "0", displayed: true)
    sendEvent(name: "currentEnergyCostYear", value: "0", displayed: true)

    sendEvent(name: "cumulativeEnergyCostHour", value: "0", displayed: true)
    sendEvent(name: "cumulativeEnergyCostWeek", value: "0", displayed: true)
    sendEvent(name: "cumulativeEnergyCostMonth", value: "0", displayed: true)
    sendEvent(name: "cumulativeEnergyCostYear", value: "0", displayed: true)
	*/
    
    delayBetween([
            formatCommand(zwave.switchMultilevelV1.switchMultilevelGet()),
            formatCommand(zwave.meterV3.meterGet(scale: 0)), // energy kWh
            formatCommand(zwave.meterV3.meterGet(scale: 1)), // energy kVAh
            formatCommand(zwave.meterV3.meterGet(scale: 2)), // watts
            formatCommand(zwave.meterV3.meterGet(scale: 4)), // volts
            formatCommand(zwave.meterV3.meterGet(scale: 5)), // amps
            formatCommand(zwave.configurationV1.configurationGet(parameterNumber: 0x51)), // device state
            formatCommand(zwave.configurationV1.configurationGet(parameterNumber: 0x53)), // night light RGB value
            formatCommand(zwave.configurationV1.configurationGet(parameterNumber: 0x54)), // led brightness
    ], 200)
}


/*******************************************************************************
 * 	Methods                                                                    *
 ******************************************************************************/

/**
 *  installed - Called when the device handling is being installed
 */
def installed() {
    log.debug "installed() called"

    if (state.deviceInfo == null) {
        state.deviceInfo = [:]
        state.deviceInfo['secureInclusion'] = false
    }
}

/**
 *  updated - Called when the preferences of the device type are changed
 */
def updated() {
    logDebug "updated()"

    response(configure())
}

/**
 *  reset - Resets the devices energy usage meter and attempt to reset device
 *
 *  Defined by the custom command "reset"
 */
def reset() {
    logDebug "reset()"
    state.energyMeterRuntimeStart = now()

    delayBetween([
            formatCommand(zwave.meterV3.meterReset()),
            formatCommand(zwave.meterV3.meterGet(scale: 0)), // energy kWh
            formatCommand(zwave.meterV3.meterGet(scale: 1)), // energy kVAh
            formatCommand(zwave.meterV3.meterGet(scale: 2)), // watts
            formatCommand(zwave.meterV3.meterGet(scale: 4)), // volts
            formatCommand(zwave.meterV3.meterGet(scale: 5)), // amps
    ], 200)
}

def getDeviceInfo() {
    logDebug "getDeviceInfo()"

    delayBetween([
            formatCommand(zwave.versionV1.versionGet()),
            formatCommand(zwave.firmwareUpdateMdV2.firmwareMdGet()),
            //zwave.manufacturerSpecificV2.deviceSpecificGet().format(),
            formatCommand(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
    ], 200)
}

private updateDeviceInfo() {
    logTrace "updateDeviceInfo()"
	
    if (state.deviceInfo == null) {
        state.deviceInfo = [:]
	}

    def buffer = "Get Device Info";
    def newBuffer = null;

    def switchStatus = "SWITCH DISABLED\n"

    if (state.deviceInfo['applicationVersion'] == null ||
        state.deviceInfo['manufacturerName'] == null) {
        getDeviceInfo()
    } else {
        newBuffer = "${switchStatus}"
    }

    if (state.deviceInfo['applicationVersion'] != null) {
        if (newBuffer == null) {
            newBuffer = "${switchStatus}"
        }

        newBuffer += "app Version: ${state.deviceInfo['applicationVersion']} Sub Version: ${state.deviceInfo['applicationSubVersion']}\n";
        newBuffer += "zWaveLibrary Type: ${state.deviceInfo['zWaveLibraryType']}\n";
        newBuffer += "zWaveProtocol Version: ${state.deviceInfo['zWaveProtocolVersion']} Sub Version: ${state.deviceInfo['zWaveProtocolSubVersion']}\n";
        newBuffer += "secure inclusion: ${state.deviceInfo['secureInclusion'] || secureInclusionOverride}\n";
    }

    if (state.deviceInfo['manufacturerName'] != null) {
        if (newBuffer == null) {
            newBuffer = "${switchStatus}"
        }

        newBuffer += "manufacturer Name: ${state.deviceInfo['manufacturerName']}\n";
        newBuffer += "manufacturer Id: ${state.deviceInfo['manufacturerId']}\n";
        newBuffer += "product Id: ${state.deviceInfo['productId']} Type Id: ${state.deviceInfo['productTypeId']}\n";
        newBuffer += "firmwareId: ${state.deviceInfo['firmwareId']} checksum: ${state.deviceInfo['checksum']}\n";
    }

    return sendEvent(name: "deviceInfo", value: "$newBuffer", displayed: false)
}

void logDebug(str) {
    if (isLogLevelDebug) {
        log.debug str
    }
}

void logTrace(str) {
    if (isLogLevelTrace) {
        log.trace str
    }
}

def formatCommand(hubitat.zwave.Command cmd) {
    if (isSecured()) {
        logTrace "Formatting secured command: ${cmd}"
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        logTrace "Formatting unsecured command: ${cmd}"
        cmd.format()
    }
}

def isSecured() {
    (state.deviceInfo && state.deviceInfo['secureInclusion']) || secureInclusionOverride
}

def internalCreateEvent(event) {
    if (forceStateChangeOnReport) {
        event.isStateChange = true
    }

    return createEvent(event)
}