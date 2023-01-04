/*
 * Enphase Envoy-S (metered) get production data with token
 *
 * Hubitat connecting to the Enphase Envoy-S (metered) with new firmware that requires a token to access local data
 *
 * Production output from Envoy : [wattHoursToday:xx, wattHoursSevenDays:xx, wattHoursLifetime:xx, wattsNow:xx]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at:
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 * Original driver by Supun Vidana Pathiranage https://community.hubitat.com/t/release-new-enphase-envoy-driver-supports-token-based-authentication/101836
 *
 * v1.0.0
 *
 */

metadata {
    definition(name: 'Enphase Envoy', namespace: 'augoisms', author: 'Justin Walker') {
        capability 'Sensor'
        capability 'Power Meter'
        capability 'Refresh'

        attribute 'energy_today', 'number'
        attribute 'energy_last7days', 'number'
        attribute 'energy_life', 'number'
    }

    preferences {
        input name: 'ip', type: 'text', title: 'Envoy local IP', required: true
        input name: 'email', type: 'text', title: 'Enlighten Email', required: true
        input name: 'password', type: 'password', title: 'Enlighten password', required: true
        input name: 'serial', type: 'text', title: 'Envoy Serial Number', required: true
        input name: 'polling', type: 'text', title: 'Polling Interval (mins)', required: true, defaultValue: '15', range: 2..59
        input name: 'logEnable', type: 'bool', title: 'Enable debug logging', defaultValue: true
    }
}

void installed() {
    installedUpdated()
}

void updated() {
    installedUpdated()
}

void installedUpdated() {
    log.info 'updated()'

    unschedule()
    state.clear()

    // schedule polling
    def sec = Math.round(Math.floor(Math.random() * 60))
    def min = Math.round(Math.floor(Math.random() * settings.polling.toInteger()))
    String cron = "${sec} ${min}/${settings.polling.toInteger()} * * * ?" // every N min
    schedule(cron, pullData)

    if (logEnable) runIn(1800, disableLogging)
}

void refresh() {
    pullData()
}

void pullData() {
    String production_url = 'https://' + settings.ip + '/api/v1/production'
    Map production_data = [:]

    logDebug 'Pulling data...'
    String token = getToken()
    if (token != null) {
        Map<String> headers = [
            'Authorization': 'Bearer ' + token
        ]
        Map<String, Object> httpParams = [
            'uri'               : production_url,
            'contentType'       : 'application/json',
            'requestContentType': 'application/json',
            'ignoreSSLIssues'   : true,
            'headers'           : headers
        ]

        try {
            httpGet(httpParams) { resp ->
                if (logEnable) {
                    if (resp.data) log.debug "${resp.data}"
                }
                if (resp.success) {
                    production_data = resp.data
                }
            }
        } catch (Exception e) {
            log.warn "HTTP get failed: ${e.message}"
        }

        sendEvent(name: 'energy_today', value: formatWatts(production_data?.wattHoursToday), unit: 'kWh')
        sendEvent(name: 'energy_last7days', value: formatWatts(production_data?.wattHoursSevenDays), unit: 'kWh')
        sendEvent(name: 'energy_life', value: formatWatts(production_data?.wattHoursLifetime), unit: 'kWh')
        sendEvent(name: 'power', value: formatWatts(production_data?.wattsNow), unit: 'kW')

    } else
        log.warn 'Unable to get a valid token. Aborting...'
}

boolean isValidToken(String token) {
    boolean valid_token = false
    String response
    String token_check_url = 'https://' + settings.ip + '/auth/check_jwt'

    logDebug 'Validating the token'
    Map<String> headers = [
        'Authorization': 'Bearer ' + token
    ]
    Map<String, Object> httpParams = [
        'uri'               : token_check_url,
        'contentType'       : 'text/html',
        'requestContentType': 'application/json',
        'ignoreSSLIssues'   : true,
        'headers'           : headers
    ]
    try {
        httpGet(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                response = resp.data
            }
        }
    } catch (Exception e) {
        log.warn "HTTP get failed: ${e.message}"
    }
    if (response?.contains('Valid token.')) {
        valid_token = true
    }
    return valid_token
}

String getSession() {
    String session
    String login_url = 'https://enlighten.enphaseenergy.com/login/login.json'

    logDebug 'Generating a session'
    Map<String> data = [
        'user[email]'    : settings.email,
        'user[password]' : settings.password
    ]
    Map<String, Object> httpParams = [
        'uri' : login_url,
        'body': data
    ]
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "${resp.data}"
            }
            if (resp.success) {
                session = resp.data?.session_id
            }
        }
    } catch (Exception e) {
        log.warn "HTTP post failed: ${e.message}"
    }

    logDebug "Session Id: ${session}"
    return session
}

String getToken() {
    logDebug 'Retrieving the token'

    String valid_token
    String current_token = getDataValue('jwt_token')

    // we have an existing token, check if it's valid
    if (current_token != null && isValidToken(current_token)) {
        logDebug 'Current token is still valid. Using it.'
        valid_token = current_token
    }
    // we don't have a token or it's invalid
    else {
        logDebug 'Current token is expired. Generating a new one.'
        String session = getSession()
        if (session != null) {
            String token_generated = generateToken(session)
            // check if generated token is valid
            if (token_generated != null && isValidToken(token_generated)) {
                updateDataValue('jwt_token', token_generated)
                valid_token = token_generated
            } else {
                log.warn 'Generated token is not valid. Investigate with debug logs'
            }
        } else {
            log.warn 'Generated token is null. Investigate with debug logs'
        }
    }
    return valid_token
}

String generateToken(String session_id) {
    String token
    String tokenUrl = 'https://entrez.enphaseenergy.com/tokens'

    logDebug 'Generating a new token'
    Map<String> data = [
            'session_id': session_id,
            'serial_num': settings.serial,
            'username'  : settings.email
    ]
    Map<String, Object> httpParams = [
            'uri'               : tokenUrl,
            'contentType'       : 'text/html',
            'requestContentType': 'application/json',
            'body'              : data
    ]
    logDebug "HTTP params: ${httpParams}"
    try {
        httpPost(httpParams) { resp ->
            if (logEnable) {
                if (resp.data) log.debug "HTTP response: ${resp.data}"
            }
            if (resp.success) {
                token = resp.data
            }
        }
    } catch (Exception e) {
        log.warn "HTTP post failed: ${e.message}"
    }
    logDebug "Generated token : ${token}"
    return token
}

BigDecimal formatWatts(def value) {
    value = value ?: 0.0
    BigDecimal watts = value.toBigDecimal()

    return round1(watts / 1000)
}

void logDebug(str) {
    if (settings.logEnable) {
        log.debug str
    }
}

void disableLogging() {
    log.warn 'debug logging disabled...'
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

BigDecimal round1(BigDecimal value) {
    return Math.round(value * 10) / 10
}