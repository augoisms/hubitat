/**
 *  AirNow.gov Virtual Sensor
 *
 *  Copyright (c) 2019 Justin Walker
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 *  based on bspranger: https://github.com/bspranger/Hubitat_AirNow/
 *  based on jschlackman: https://github.com/jschlackman/AirNow
 *
 *
 *  V1.0.2 - refactored to use async calls
 *  V1.0.1 - added switch attr to denote activate state when combined category is above 2
 *  V1.0.0 - Initial version
 *
 */
metadata {
	definition (name: "AirNow Virtual Sensor", namespace: "augoisms", author: "Justin Walker") {
		capability "Sensor"
        capability "Polling"

		attribute "combined", "number" // Combined AQI value (worst of either Ozone or PM2.5)
		attribute "combinedCategory", "number" // Combined AQI category number
		attribute "combinedCategoryName", "string" // Combined AQI category name
		attribute "O3", "number" // Ozone AQI value
		attribute "O3Category", "number" // Ozone AQI category number
		attribute "O3CategoryName", "string" // Ozone AQI category name
		attribute "Pm25", "number" // PM2.5 AQI value
		attribute "Pm25Category", "number" // PM2.5 AQI category number
		attribute "Pm25CategoryName", "string" // PM2.5 AQI category name
		attribute "reportingLocation", "string" // City or area name of observed data, with 2-letter state code. Also used to display errors in the mobile app UI.
        attribute "dateObserved", "string" // Date of observation (yyyy-mm-dd)
        attribute "hourObserved", "number" // Hour of observation (00-23)
        attribute "latitude", "number" // Latitude of observation area in decimal degrees.
        attribute "longitude", "number" // Longitude of observation area in decimal degrees.
        
        attribute "switch", "enum", ["on", "off"]

		command "refresh"
	}

	preferences {
		input name: "zipCode", type: "text", title: "Zip Code (optional)", required: false
		input name: "airNowKey", type: "text", title: "AirNow API Key", required: true, description: "Register at airnowapi.org"
        input name: "distance", type: "number", title: "Max. Observation Distance (miles)", required: false, description: "Default: 25 miles."
	}

	tiles(scale: 2) {
		
        // Combined AQI number tile, used only for Things view
		standardTile("mainTile", "device.combined", width: 2, height: 2, decoration: "flat", canChangeIcon: true) {
			state "default", label:'${currentValue}', icon: "st.Outdoor.outdoor25", // Defaults to tree icon from ST Outdoor category
                backgroundColors:[
                    [value: 25, color: "#44b621"],
                    [value: 60, color: "#f1d801"],
                    [value: 110, color: "#d04e00"],
                    [value: 165, color: "#bc2323"],
                    [value: 220, color: "#693591"],
                    [value: 320, color: "#7e0023"],
                ]
        	}

		// Ozone AQI category
		standardTile("O3CategoryName", "device.O3CategoryName", width: 4, height: 2, decoration: "flat") {
			state "default", label:'Ozone Level: ${currentValue}'
		}
        
		// Ozone AQI value
        valueTile("O3", "device.O3", width: 2, height: 2) {
			state "default", label:'${currentValue}',
                backgroundColors:[
                    [value: 25, color: "#44b621"],
                    [value: 60, color: "#f1d801"],
                    [value: 110, color: "#d04e00"],
                    [value: 165, color: "#bc2323"],
                    [value: 220, color: "#693591"],
                    [value: 320, color: "#7e0023"],
                ]
		}

		// PM2.5 AQI category
		standardTile("Pm25CategoryName", "device.Pm25CategoryName", width: 4, height: 2, decoration: "flat") {
			state "default", label:'PM₂.₅ Level: ${currentValue}'
		}
        
		// PM2.5 AQI value
        valueTile("Pm25", "device.Pm25", width: 2, height: 2) {
			state "default", label:'${currentValue}',
                backgroundColors:[
                    [value: 25, color: "#44b621"],
                    [value: 60, color: "#f1d801"],
                    [value: 110, color: "#d04e00"],
                    [value: 165, color: "#bc2323"],
                    [value: 220, color: "#693591"],
                    [value: 320, color: "#7e0023"],
                ]
		}

		// Observation location
		standardTile("reportingArea", "device.reportingLocation", width: 4, height: 2, decoration: "flat") {
			state "default", label:'Observation Location: ${currentValue}'
		}

		// Refresh button
		standardTile("refresh", "device.combined", width: 2, height: 2, decoration: "flat") {
			state "default", label: "", action: "refresh", icon:"st.secondary.refresh"
		}

		// Tile layout for main listing and details screen
		main("mainTile")
		details("aqi", "O3CategoryName", "O3", "Pm25CategoryName", "Pm25", "reportingArea", "refresh")
	}
}

// Parse events into attributes. This will never be called but needs to be present in the DTH code.
def parse(String description) {
	log.debug("AirNow: Parsing '${description}'")
}

def installed() {
    runEvery1Hour(poll)
	poll()
}

def updated() {
	poll()
}

def uninstalled() {
	unschedule()
}

// handle commands
def poll() {
	log.debug("Polling AirNow for air quality data, location: ${location.name}")

	if(airNowKey) {
		
		def airZip = null
        def airDistance = 0

		// Use hub zipcode if user has not defined their own
		if(zipCode) {
			airZip = zipCode
		} else {
			airZip = location.zipCode
		}
        
        // Set the user's requested observation distance, or use a default of 25 miles
        if(distance) {
        	airDistance = distance
        } else {
        	airDistance = 25
        }

		// Set up the AirNow API query
		def params = [
			uri: 'http://www.airnowapi.org/aq/',
			path: 'observation/zipCode/current/',
			contentType: 'application/json',
			query: [format:'application/json', zipCode: airZip, distance: airDistance, API_KEY: airNowKey]
		]

         asynchttpGet('requestCallback', params)
	}
	
	else {
		log.warn "No AirNow API key specified."
        send(name: "reportingLocation", value: "No AirNow API key specified in device settings")
	}
}

def requestCallback(response, data) {

    if (response.hasError()) {
        log.error('Request failed')
        log.error(response.getErrorMessage())
        send(name: "reportingLocation", value: "Could not retreive data: check API key in device settings")
        return
    }

    def newCombined = -1
    def newCombinedCategory = -1
    def newCombinedCategoryName = ''

    // Parse the observation data array
    def respData = response.getJson()
    respData.each {observation ->

        if (observation.ParameterName == "O3") {
            send(name: "O3", value: observation.AQI)
            send(name: "O3Category", value: observation.Category.Number)
            send(name: "O3CategoryName", value: observation.Category.Name)
        }
        else if (observation.ParameterName == "PM2.5") {
            send(name: "Pm25", value: observation.AQI)
            send(name: "Pm25Category", value: observation.Category.Number)
            send(name: "Pm25CategoryName", value: observation.Category.Name)
        }	
        
        // Check if the observation currently being parsed has the highest AQI and should therefore be the combined AQI
        if ((observation.AQI > newCombined) || (observation.Category.Number > newCombinedCategory)) {
            newCombined = observation.AQI
            newCombinedCategory = observation.Category.Number
            newCombinedCategoryName = observation.Category.Name
        }
        
    }
    
    // Send the combined AQI
    send(name: "combined", value: newCombined, unit: "AQI")
    send(name: "combinedCategory", value: newCombinedCategory)
    send(name: "combinedCategoryName", value: newCombinedCategoryName)
    
    // set the switch (active) state based on the category
    def activeState = newCombinedCategory > 2 ? "on" : "off"
    send(name: "switch", value: activeState)

    // Send the first reporting area (it will be the same for both observations)
    send(name: "reportingLocation", value: "${respData[0].ReportingArea} ${respData[0].StateCode}")
    send(name: "latitude", value: respData[0].Latitude)
    send(name: "longitude", value: respData[0].Longitude)
    send(name: "dateObserved", value: respData[0].DateObserved)
    send(name: "hourObserved", value: respData[0].HourObserved)

    log.debug("Sucessfully retrieved air quality data from AirNow.")
}

def refresh() {
	poll()
}

def configure() {
	poll()
}

private send(map) {
	//log.debug "AirNow: event: $map"
	sendEvent(map)
}