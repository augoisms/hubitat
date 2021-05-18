/**
 * Somfy ZRTSI driver for Hubitat
 *
 * Copyright (c) 2019 Justin Walker
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 *  v1.0.0 - Initial version
 *  v1.0.1 - Added option to reverse on/off direction (2021-05-18)
 *
 */

import groovy.transform.Field

metadata {
	definition (name: "Somfy ZRTSI", namespace: "augoisms", author: "Justin Walker", importUrl: "https://raw.githubusercontent.com/augoisms/hubitat/master/somfy-zrtsi/somfy-zrtsi.driver.groovy") {
		capability "Actuator"
		capability "Switch"
		capability "Window Shade"
		
		command "myPosition"	

		fingerprint mfr: "0047", prod:"5A52", deviceId: "5401", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH1"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5402", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH2"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5403", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH3"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5404", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH4"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5405", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH5"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5406", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH6"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5407", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH7"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5408", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH8"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5409", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH9"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5410", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH10"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5411", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH11"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5412", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH12"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5413", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH13"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5414", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH14"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5415", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH15"
		fingerprint mfr: "0047", prod:"5A52", deviceId: "5416", inClusters: "0x2C,0x72,0x26,0x20,0x25,0x2B,0x86", deviceJoinName: "Somfy ZRTSI CH16"
	}

	preferences {
		input name: "travelDuration", type: "number", title:"<b>Travel Duration</b> (seconds)", description: "<div><i>Approximate duration that it takes your shade to open/close.</i></div><br>", required: true, defaultValue: 6
		input name: "myPositionlevel", type: "bool", title:"<b>50% For My Position</b>", description: "<div><i>If the setPosition value is 50 and this option is enabled, the My/Stop command will be sent instead.</i></div><br>", required: true, defaultValue: true
		input name: "reverseDirection", type: "bool", title: "Reverse On/Off Direction?", defaultValue: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
	}
}

// Scene Actuator Conf (1), Manufacture Specific (2), Switch Multilevel (3), Basic (1), Switch Binary (1), Scene Activation (1), Version (1) 
@Field static Map CMD_CLASS_VERS = [0x2C:1, 0x72:2, 0x26:3, 0x20:1, 0x25:1, 0x2B:1, 0x86:1]

void installed() {
	pollDeviceData()
}

void updated() {
	pollDeviceData()
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
	cmds.add(zwave.versionV1.versionGet())
    sendToDevice(cmds) 
}

///
/// z-wave message handling
///

void parse(String description) {
    logDebug "parse: ${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) { zwaveEvent(cmd) }
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	logDebug "manufacturerId:   ${cmd.manufacturerId}"
	logDebug "manufacturerName: ${cmd.manufacturerName}"
	logDebug "productId:        ${cmd.productId}"
	logDebug "productTypeId:    ${cmd.productTypeId}"

	updateDataValue("manufacturerId", "${cmd.manufacturerId}")
	updateDataValue("manufacturerName", "${cmd.manufacturerName}")
	updateDataValue("productId", "${cmd.productId}")
	updateDataValue("productTypeId", "${cmd.productTypeId}")
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    logDebug "version1 report: ${cmd}"
    updateDataValue("firmwareVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
    updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    updateDataValue("zWaveLibraryType", "${cmd.zWaveLibraryType}")
}

void zwaveEvent(hubitat.zwave.Command cmd) {
	// handles any commands we are not interested in
	// we ignore most reports as the ZRTSI doesn't accurately report its state
	logDebug "Unhandled Event ${cmd}"
}

void sendToDevice(List<hubitat.zwave.Command> cmds) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd) {
    sendHubCommand(new hubitat.device.HubAction(cmd.format(), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
    return delayBetween(cmds.collect{ it.format() }, delay)
}

///
/// commands
///

@Field static BigDecimal COMMAND_ON = 0xFF

@Field static BigDecimal COMMAND_OFF = 0x00

void on() {
	logDebug "on()"	
	sendToDevice(zwave.basicV1.basicSet(value: reverseDirection ? COMMAND_OFF : COMMAND_ON))	
	sendOpeningClosingEvent(99)
	runIn(travelDuration, 'sendEvents', [data: [level: 99]]);
}

void off() {
	logDebug "off()"	
	sendToDevice(zwave.basicV1.basicSet(value: reverseDirection ? COMMAND_ON : COMMAND_OFF))
	sendOpeningClosingEvent(0)
	runIn(travelDuration, 'sendEvents', [data: [level:0]]);
}

void open()  { on() }

void close() { off() }

void myPosition() {
	logDebug "myPosition()"
    sendToDevice(zwave.switchMultilevelV3.switchMultilevelStopLevelChange()) 
	sendOpeningClosingEvent(50)
	runIn(travelDuration, 'sendEvents', [data: [level:50]]);
}

void setPosition(level) {
	setLevel(level)
}

private void setLevel(level) {
	logDebug "setLevel(${level})."

	// make sure level is within range
	if(level >= 99) { level = 99 } 
	if(level < 0) { level = 0 }

	if (myPositionlevel && level == 50) {
		sendToDevice(zwave.switchMultilevelV3.switchMultilevelStopLevelChange()) 
	}
	else {
		sendToDevice(zwave.switchMultilevelV3.switchMultilevelSet(value: level.toInteger(), dimmingDuration: 1))
	}
	
	sendOpeningClosingEvent(level)
	runIn(travelDuration, 'sendEvents', [data: [level: level]]);		
}

private void sendOpeningClosingEvent(level) {
	if (level == 0) { 
		sendEvent(name: "windowShade", value: "closing") 
	}
	else { 
		sendEvent(name: "windowShade", value: "opening")
	}
}

private void sendEvents(Map data) {
	def level = data.level

	if (level == 0) { 
		sendEvent(name: "switch", value: "off")
		sendEvent(name: "windowShade", value: "closed")
		if (txtEnable) log.info "${device.displayName} was closed"
	}
	else if (level < 99) {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "windowShade", value: "partially open")
		if (txtEnable) log.info "${device.displayName} was partially opened"
	}
	else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "windowShade", value: "open")
		if (txtEnable) log.info "${device.displayName} was opened"
	}

	if (txtEnable) log.info "${device.displayName} was set to ${level}"
	sendEvent(name: "position", value: level, unit: "%")
}

///
/// helper functions
///

private void logDebug(str) {
    if (logEnable) {
        log.debug str
    }
}

