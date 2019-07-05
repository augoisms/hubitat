/**
 * SunCalc Driver for Hubitat
 *
 * A port of the SunCalc.js library https://github.com/mourner/suncalc
 * 
 *
 * Altitude is the angle up from the horizon. Zero degrees altitude means exactly on your local horizon, and 90 degrees is "straight up". Hence, "directly underfoot" is -90 degrees altitude. 
 * Azimuth is the angle along the horizon, with zero degrees corresponding to North, and increasing in a clockwise fashion. Thus, 90 degrees is East, 180 degrees is South, and 270 degrees is West. 
 * Using these two angles, one can describe the apparent position of an object (such as the Sun at a given time).
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
 *  V1.0.0 - Initial version
 *
 */

metadata {
    definition (name: "SunCalc Driver", namespace: "augoisms", author: "Justin Walker") {
        capability "Actuator"
        capability "Sensor"

        command "refresh"

        attribute "altitude", "number"
        attribute "azimuth", "number"
        attribute "lastCalculated", "string"    
    }
    preferences() {
        section("Automatic Calculations"){
            input "autoUpdate", "bool", title: "Auto Update?", required: true, defaultValue: true
            input "updateInterval", "enum", title: "Update Interval:", required: true, defaultValue: "5 Minutes", options: ["1 Minute", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]			
        }
    }
}

def updated() {
    log.debug "updated()"

    unschedule()
    refresh()

    if(autoUpdate) {
        def updateIntervalCmd = (settings?.updateInterval ?: "1 Minutes").replace(" ", "")
        "runEvery${updateIntervalCmd}"(refresh)
    }        
}
              
def parse(String description) {
}

def refresh()
{
    def coords = getPosition()
    sendEvent(name: "altitude", value: coords.altitude)
    sendEvent(name: "azimuth", value: coords.azimuth)
    sendEvent(name: "lastCalculated", value: new Date().format("yyyy-MM-dd h:mm", location.timeZone))    
}

///
/// Calculations
///

// date/time constants and conversions
def dayMs() { return 1000 * 60 * 60 * 24 }
def J1970() { return 2440588 }
def J2000() { return 2451545 }
def rad() { return  Math.PI / 180 }
def e() { return  rad() * 23.4397 } // obliquity of the Earth

def toJulian() { 
    def date = new Date()
    date = date.getTime() / dayMs() - 0.5 + J1970()
    return date   
}
def fromJulian(j)  { return new Date((j + 0.5 - J1970()) * dayMs()) }
def toDays(){ return toJulian() - J2000() }

// general calculations for position

def rightAscension(l, b) { return Math.atan2(Math.sin(l) * Math.cos(e()) - Math.tan(b) * Math.sin(e()), Math.cos(l)) }
def declination(l, b)    { return Math.asin(Math.sin(b) * Math.cos(e()) + Math.cos(b) * Math.sin(e()) * Math.sin(l)) } 

def azimuth(H, phi, dec)  { return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi)) }
def altitude(H, phi, dec) { return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H)) }

def siderealTime(d, lw) { return rad() * (280.16 + 360.9856235 * d) - lw }

// general sun calculations

def solarMeanAnomaly(d) { return rad() * (357.5291 + 0.98560028 * d) }

def eclipticLongitude(M) {

	def C = rad() * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M)) // equation of center
	def P = rad() * 102.9372 // perihelion of the Earth

    return M + C + P + Math.PI 
}

def sunCoords(d) {

    def M = solarMeanAnomaly(d)
    def L = eclipticLongitude(M)

	return [dec: declination(L, 0), ra: rightAscension(L, 0)]
}

// calculates sun position for a given date and latitude/longitude

def getPosition() {

	def lng = location.longitude
   	def lat = location.latitude
    
    def lw  = rad() * -lng    
    def phi = rad() * lat
    def d   = toDays()
    def c  = sunCoords(d)
    def H  = siderealTime(d, lw) - c.ra
     
    def az = azimuth(H, phi, c.dec)
    az = (az * 180 / Math.PI) + 180

    def al = altitude(H, phi, c.dec)
    al = al * 180 / Math.PI
    
    return [
        azimuth: az,
        altitude: al,
    ]
}

