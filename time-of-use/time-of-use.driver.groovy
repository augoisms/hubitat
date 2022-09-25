/**
 * TOU (Time of Use) driver for Hubitat
 *
 * Copyright (c) 2021 Justin Walker
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 *  v1.0.0 - Initial version
 *
 */

metadata {
    definition (name: 'TOU Rate', namespace: 'augoisms', author: 'Justin Walker') {
        capability 'Actuator'
        capability 'Sensor'

        command 'refresh'

        attribute 'description', 'string'
        attribute 'period', 'enum', ['onPeak', 'superOffPeak', 'offPeak']
        attribute 'price', 'string'   
    }
    preferences {
        input name: 'priceOnPeak', type: 'string', title: '<b>On-Peak Price</b>'
        input name: 'priceSuperOffPeak', type: 'string', title: '<b>Super Off-Peak Price</b>'
        input name: 'priceOffPeak', type: 'string', title: '<b>Off-Peak Price</b>'
    }
}

def updated() {
    log.debug 'updated()'

    unschedule()
    refresh()
 
    runEvery1Minute(refresh)      
}
              
def parse(String description) { }

def refresh() {
    Calendar cal = Calendar.getInstance();
    def hour = cal.get(Calendar.HOUR_OF_DAY)
    int dow = cal.get (Calendar.DAY_OF_WEEK);
    boolean isWeekday = ((dow >= Calendar.MONDAY) && (dow <= Calendar.FRIDAY));

    // on-peak
    // 4:00 pm - 9:00 pm (everyday)
    if (hour >= 16 && hour < 21) {
        sendEvent(name: 'description', value: 'On-Peak')
        sendEvent(name: 'period', value: 'onPeak')
        sendEvent(name: 'price', value: priceOnPeak)
    }
    // super off-peak
    // midnight - 6:00 am (weekdays)
    else if (isWeekday && hour >= 0 && hour < 6) {
        sendEvent(name: 'description', value: 'Super Off-Peak')
        sendEvent(name: 'period', value: 'superOffPeak')
        sendEvent(name: 'price', value: priceSuperOffPeak)
    }
    // super off-peak
    // midnight - 2:00 pm (weekends & holidays)
    else if (!isWeekday && hour >= 0 && hour < 14) {
        sendEvent(name: 'description', value: 'Super Off-Peak')
        sendEvent(name: 'period', value: 'superOffPeak')
        sendEvent(name: 'price', value: priceSuperOffPeak)
    }
    // off-peak
    // all other hours
    else {
        sendEvent(name: 'description', value: 'Off-Peak')
        sendEvent(name: 'period', value: 'offPeak')
        sendEvent(name: 'price', value: priceOffPeak)
    }  
}