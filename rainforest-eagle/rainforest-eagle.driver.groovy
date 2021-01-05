/**
 *  Rainforest Eagle Driver - Implements the uploader API
 *
 * Copyright (c) 2020 Justin Walker
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 *  v1.0.0 - Initial version (2020-04-26)
 *  v1.0.1 - Added health check (2020-12-12)
 *  v1.0.2 - Fix for parsing Eagle 200 summation (2020-12-18)
 *  v1.0.3 - Added support for price & cost (2021-01-05)
 *
 */

import groovy.transform.Field

metadata {
    definition(name: 'Rainforest Eagle', namespace: 'augoisms', author: 'Justin Walker') {
        capability 'PowerMeter'
        capability 'EnergyMeter'
        capability 'Sensor'

        command 'resetEnergy'

        attribute 'price', 'number'
        attribute 'cost', 'number'
        attribute 'costPerHour', 'number'
    }
}

preferences {
    input name: 'eagleIP', type: 'string', title:'<b>Eagle IP Address</b>', description: '<div><i>Please use a static IP.</i></div><br>', required: true
    input name: 'reportWatts', type: 'bool', title:'<b>Report Power in Watts?</b>', description: '<div><i>Default reporting is in kW. Energy is always in kWh.</i></div><br>', defaultValue: true
    input name: 'autoResetEnergy', type: 'enum', title: '<b>Automatically Reset Energy</b>', description: '<div><i>Reset energy on the specified day every month.</i></div></br>', options: daysOptions, defaultValue: 'Disabled'
    input name: 'loggingEnabled', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false
}

def installed() {
    log.debug 'RainforestEagle device installed'
    installedUpdated()
}

def updated() {
    installedUpdated()
}

void installedUpdated() {
    unschedule()

    setNetworkAddress()

    // disable logging in 30 minutes
    if (settings.loggingEnabled) runIn(1800, disableLogging)

    // schedule auto reset
    if (autoResetEnergy && autoResetEnergy != 'Disabled') {
        schedule("0 0 0 ${autoResetEnergy} * ? *", 'resetEnergy')
    }

    // perform health check every 1 minutes
    runEvery1Minute('healthCheck') 
}

// parse events into attributes
def parse(String description) {
    logDebug "Parsing '${description}'"

    def msg = parseLanMessage(description)   
    def body = new XmlSlurper().parseText(new String(msg.body))
    logDebug groovy.xml.XmlUtil.escapeXml(msg.body) 

    if (body?.InstantaneousDemand?.Demand.text()) { 
        parseInstantaneousDemand(body.InstantaneousDemand)
    }

    if (body?.DeviceInfo?.DeviceMacId.text()) { 
        parseDeviceInfo(body.DeviceInfo)
    }

    if (body?.NetworkInfo?.DeviceMacId.text()) { 
        parseNetworkInfo(body.NetworkInfo)
    }

    if (body?.ConnectionStatus?.DeviceMacId.text()) { 
        parseNetworkInfo(body.ConnectionStatus)
    }

    if (body?.CurrentSummationDelivered?.SummationDelivered.text()) {
        // EAGLE 100
        parseCurrentSummation(body.CurrentSummationDelivered)
    }

    if (body?.CurrentSummation?.SummationDelivered.text()) {
        // EAGLE 200
        parseCurrentSummation(body.CurrentSummation)
    }

    if (body?.PriceCluster?.Price.text()) {
        parsePriceCluster(body.PriceCluster)
    }

    state.lastReport = now()
}

void resetEnergy() {
    // reset engery starting point
    state.remove('energyStart')
    state.remove('energyStartTimestamp')
    sendEvent(name: 'energy', value: 0, unit: 'kWh')
}

void setNetworkAddress() {
    // Setting Network Device Id
    def dni = convertIPtoHex(settings.eagleIP)
    if (device.deviceNetworkId != "$dni") {
        device.deviceNetworkId = "$dni"
        log.debug "Device Network Id set to ${device.deviceNetworkId}"
    }

    // set hubitat endpoint
    state.hubUrl = "http://${location.hub.localIP}:39501"
}

void parseInstantaneousDemand(InstantaneousDemand) {
    logDebug "Adding InstantaneousDemand"
    
    int demand = convertHexToInt(InstantaneousDemand.Demand.text())

    int multiplier = convertHexToInt(InstantaneousDemand.Multiplier.text())
    if (multiplier == 0) { multiplier = 1 }

    int divisor = convertHexToInt(InstantaneousDemand.Divisor.text())
    if (divisor == 0) { divisor = 1 }
    
    def value = (demand * multiplier) / divisor
    def reportedValue = reportWatts ? Math.round(value * 1000) : value
    def unit = reportWatts ? 'W' : 'kW'
    
    logDebug "Current Demand: ${reportedValue}"
    
    sendEvent(name: 'power', value: reportedValue, unit: unit)

    // calculate estimated cost per hour
    def costPerHour = round2(value * (device.currentValue('price') ?: 0))
    sendEvent(name: 'costPerHour', value: costPerHour)
}

void parseDeviceInfo(deviceInfo) {
    updateDeviceData('zigbeeMacId', deviceInfo.DeviceMacId.text())
    updateDeviceData('installCode', deviceInfo.InstallCode.text())
    updateDeviceData('linkKey', deviceInfo.LinkKey.text())
    updateDeviceData('fwVersion', deviceInfo.FWVersion.text())
    updateDeviceData('hwVersion', deviceInfo.HWVersion.text())
    updateDeviceData('imageType', deviceInfo.ImageType.text())
    updateDeviceData('modelId', deviceInfo.ModelId.text())
    updateDeviceData('dateCode', deviceInfo.DateCode.text())
    updateDeviceData('installCode', deviceInfo.InstallCode.text())
}

void parseNetworkInfo(networkInfo) {
    if (networkInfo.Protocol.text()) {
        // Eagle 200
        updateDeviceData('meterMacId', networkInfo.MeterMacId.text())
    }
    else {
        // Eagle 100   
        updateDeviceData('meterMacId', networkInfo.CoordMacId.text())    
    }    
    state.connectionStatus = networkInfo.Status.text()
    state.channel = networkInfo.Channel.text()
    state.connectionStrength = convertHexToInt(networkInfo.LinkStrength.text()) + "%"
}

void parseCurrentSummation(summation) {
    int delivered = convertHexToInt(summation.SummationDelivered.text())
    int received = convertHexToInt(summation.SummationReceived.text())

    // TimeStamp - 8 hex digits 
    // UTC Time (offset in seconds from 00:00:00 01Jan2000) when data received from meter.
    int timestamp = convertHexToInt(summation.TimeStamp.text())
    def dateString = utc2000ToDate(timestamp) 

    int multiplier = convertHexToInt(summation.Multiplier.text())
    if (multiplier == 0) { multiplier = 1 }

    int divisor = convertHexToInt(summation.Divisor.text())
    if (divisor == 0) { divisor = 1 }

    def deliveredValue = (delivered * multiplier) / divisor
    def receivedValue = (received * multiplier) / divisor

    state.summationDelivered = deliveredValue
    state.summationReceived = receivedValue
    state.summationTimestamp = dateString
    
    if (state.energyStart) {
        // calculate energy
        def totalEnergy = round2(deliveredValue - state.energyStart)
        sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')

        // calculate cost of total energy
        def costEnergy = round2(totalEnergy * (device.currentValue('price') ?: 0))
        sendEvent(name: 'cost', value: costEnergy)
    }
    else {
        // save value
        state.energyStart = deliveredValue
        state.energyStartTimestamp = dateString
        sendEvent(name: 'energy', value: 0, unit: 'kWh')
        sendEvent(name: 'cost', value: 0)
    }
}

void parsePriceCluster(priceCluster) {
    int price = convertHexToInt(priceCluster.Price.text())
    int trailingDigits = convertHexToInt(priceCluster.TrailingDigits.text())

    def priceKwh = price > 0 ? Double.parseDouble("${price}e-${trailingDigits}") : 0
    sendEvent(name: 'price', value: priceKwh)
}

void updateDeviceData(String key, String value) {
    if (device.data[key] != value) {
        updateDataValue(key, value)
    }
}

void healthCheck() {
    if (state.lastReport != null) {
        // check if there have been any reports in the last 1 minute
        if(state.lastReport >= now() - (1 * 60 * 1000)) {
            // healthy
            logDebug 'healthCheck: healthy'
        }
        else {
            // not healthy
            log.warn 'healthCheck: not healthy'
            // if we don't receive any messages within 1 minute
            // set power to 0
            def unit = reportWatts ? 'W' : 'kW'
            sendEvent(name: 'power', value: '0', unit: unit)
        }
    }
    else {
        log.info 'No previous reports. Cannot determine health.'
    }
}

private Integer convertHexToInt(hex) {
    return new BigInteger(hex[2..-1], 16)
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()

}

private String utc2000ToDate(int seconds) {
    int unix200Time = 946684800
    // <UNIX time> = <2000 time> + <January 1, 2000 UNIX time>
    int unixSeconds = seconds + unix200Time
    long unixMilliseconds = unixSeconds * 1000L
    new Date(unixMilliseconds).format('yyyy-MM-dd h:mm', location.timeZone)
}

void disableLogging() {
	log.info 'Logging disabled.'
	device.updateSetting('loggingEnabled',[value:'false',type:'bool'])
}

void logDebug(str) {
    if (loggingEnabled) {
        log.debug str
    }
}

@Field static List daysOptions = ['Disabled','1','2','3','4','5','6','7','8','9','10','11','12','13','14','15','16','17','18','19','20','21','22','23','24','25','26','27','28','29','30','31']

BigDecimal round2(BigDecimal value) {
    return Math.round(value * 100) / 100
}