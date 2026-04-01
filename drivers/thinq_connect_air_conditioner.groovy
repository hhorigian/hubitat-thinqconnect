/**
 *  ThinQ Connect Air Conditioner
 *
 *  Copyright 2025
 *
 *  Uses official LG ThinQ Connect API
 *   27.12.2025 - hhorigian - Added wind functionality, added target temperature functionality
 *   Need to use a Certificate Generation tool like: https://csrgenerator.com/ 
 *   21.1.2025 - hhorigian - Added Thermostat Capability. Added commands for HomeKit compatibility Thermostat. 
 *   28.1.2025 - hhorigian - Added Set Defaults, to initialize heatingsetpoint and enable EZ Dashboards tile 
 *   21.2.2026 - hhorigian - Added Heating Mode, Cool Mode, and setpoint for heating 
 *   31.3.2026 - hhorigian - Fixed on/off switch null values returned. 
  */


import groovy.transform.Field
import groovy.json.JsonSlurper

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
    definition(name: "ThinQ Connect Air Conditioner", namespace: "jonozzz", author: "Ionut Turturica") {
        capability "Sensor"
        capability "Switch"
        capability "Initialize"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "Thermostat"


		attribute "heatingSetpoint", "string" 
        attribute "currentState", "string"
        attribute "currentJobMode", "string"
        attribute "airConOperationMode", "string"
        // attribute "airCleanOperationMode", "string"
        attribute "currentTemperature", "number"
        attribute "targetTemperature", "number"
        // attribute "minTargetTemperature", "number"
        // attribute "maxTargetTemperature", "number"
        // attribute "heatTargetTemperature", "number"
        attribute "coolTargetTemperature", "number"
        attribute "temperatureUnit", "string"
        // attribute "twoSetEnabled", "string"
        // attribute "windStrength", "string"
        // attribute "windStep", "number"
        // attribute "rotateUpDown", "string"
        // attribute "rotateLeftRight", "string"
        // attribute "light", "string"
        // attribute "powerSaveEnabled", "string"
        // attribute "airQualityMonitoringEnabled", "string"
        // attribute "pm1", "number"
        // attribute "pm2", "number"
        // attribute "pm10", "number"
        // attribute "odorLevel", "string"
        // attribute "humidity", "number"
        // attribute "totalPollutionLevel", "string"
        // attribute "filterRemainPercent", "number"
        attribute "filterUsedTime", "number"
        attribute "filterLifetime", "number"
        attribute "error", "string"
        // attribute "remoteControlEnabled", "string"
        
        // Timer attributes
        attribute "relativeHourToStart", "number"
        attribute "relativeMinuteToStart", "number"
        attribute "relativeHourToStop", "number"
        attribute "relativeMinuteToStop", "number"
        // attribute "absoluteHourToStart", "number"
        // attribute "absoluteMinuteToStart", "number"
        // attribute "absoluteHourToStop", "number"
        // attribute "absoluteMinuteToStop", "number"
        
        // Sleep timer attributes
        // attribute "sleepRelativeHourToStop", "number"
        // attribute "sleepRelativeMinuteToStop", "number"
        
        // Commands
        command "on"
        command "off"
        command "start"
        command "stop"
        command "getDeviceProfile"
        // ANTIGO command "setAirConJobMode", [[name:"Set AirConJobMode", type: "ENUM", description: "Select AirCon Job Mode", constraints: ["COOL", "ENERGY_SAVING", "AIR_DRY", "FAN"]]]
		command "setAirConJobMode", [[name:"Set AirConJobMode", type: "ENUM", description: "Select AirCon Job Mode", constraints: ["COOL", "HEAT", "ENERGY_SAVING", "AIR_DRY", "FAN"]]]
        command "setTargetTemperature", ["number"]
        command "setWindStrength", [[name:"Set Wind Strength", type: "ENUM", description: "Select Wind Strength", constraints: ["LOW", "MID", "HIGH"]]]
        command "setLight", [[name:"Set Light on Unit", type: "ENUM", description: "Green Light on AC", constraints: ["ON", "OFF"]]]

		command "setThermostatMode", [[name:"Mode*", type:"ENUM", constraints:["cool","heat"]]]        
        // command "powerOff"
        // command "setAirConOperationMode", ["string"]
        // command "setAirCleanOperationMode", ["string"]
        // command "setHeatTargetTemperature", ["number"]
        // command "setCoolTargetTemperature", ["number"]
        // command "setWindStep", ["number"]
        // command "setRotateUpDown", ["string"]
        // command "setRotateLeftRight", ["string"]
        // command "setPowerSave", ["string"]
        // command "setTwoSetEnabled", ["string"]
        command "setDelayStart", [[name:"Set Delay Start", type: "NUMBER", description: "Select Delay Start in minutes"]]
        command "setDelayStop", [[name:"Set Delay Stop", type: "NUMBER", description: "Select Delay Stop in minutes"]]
        command "unsetStopTimer"
        command "unsetStartTimer"
        // command "setAbsoluteStart", ["number", "number"]
    }

    preferences {
        section {
            input name: 'isFahrenheit', type: 'bool', title: '<b>Fahrenheit</b>', description: '<i>Use fahrenheit degrees</i>', defaultValue: true
            input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
            input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
        }
    }
}

def installed() {
    logger("debug", "installed()")
    initialize()
}

def updated() {
    logger("debug", "updated()")
    initialize()
}

def uninstalled() {
    logger("debug", "uninstalled()")
}

def initialize() {
    logger("debug", "initialize()")

    if (getDataValue("master") == "true") {
        if (interfaces.mqtt.isConnected())
            interfaces.mqtt.disconnect()

        mqttConnectUntilSuccessful()
    }
    
    refresh()
    setdefaults()
}

def refresh() {
    logger("debug", "refresh()")
    def status = parent.getDeviceState(getDeviceId())
    processStateData(status)
}

def mqttConnectUntilSuccessful() {
    logger("debug", "mqttConnectUntilSuccessful()")

    try {
        def mqtt = parent.retrieveMqttDetails()

        interfaces.mqtt.connect(mqtt.server,
                                mqtt.clientId,
                                null,
                                null,
                                tlsVersion: "1.2",
                                privateKey: mqtt.privateKey,
                                caCertificate: mqtt.caCertificate,
                                clientCertificate: mqtt.certificate,
                                cleanSession: true,
                                ignoreSSLIssues: true)
        pauseExecution(3000)
        for (sub in mqtt.subscriptions) {
            interfaces.mqtt.subscribe(sub)
        }
        return true
    }
    catch (e) {
        logger("warn", "Lost connection to MQTT, retrying in 15 seconds ${e}")
        runIn(15, "mqttConnectUntilSuccessful")
        return false
    }
}


def setdefaults() {

    //sendEvent(name: "temperature", value: convertTemperatureIfNeeded(68.0,"F",1))
    ///sendEvent(name: "thermostatSetpoint", value: "20", descriptionText: "Thermostat thermostatSetpoint set to 20")
    sendEvent(name: "heatingSetpoint", value: "25", descriptionText: "Thermostat heatingSetpoint set to 20")     
    //sendEvent(name: "coolingSetpoint", value: "19", descriptionText: "Thermostat coolingSetpoint set to 20") 
	//sendEvent(name: "hysteresis", value: (hysteresis ?: 0.5).toBigDecimal())
    //sendEvent(name: "thermostatOperatingState", value: "idle", descriptionText: "Set thermostatOperatingState to Idle")     
    //sendEvent(name: "thermostatFanMode", value: "auto", descriptionText: "Set thermostatFanMode auto")     
	//sendEvent(name: "speed", value: "auto", descriptionText: "speed set ")
    //sendEvent(name: "setHeatingSetpoint", value: "25", descriptionText: "Set setHeatingSetpoint to 25")     

    
    //Thermostat Modes Enabled 
	//setSupportedThermostatModes(JsonOutput.toJson(["auto", "cool", "heat", "off"]))
    //setSupportedThermostatModes(JsonOutput.toJson(["auto","cool","heat","off"]))
    //setSupportedThermostatModes(JsonOutput.toJson(["cool","heat","off"]))

    //FAN MODES enabled
    //setSupportedThermostatFanModes(JsonOutput.toJson(["auto"]))
    //sendEvent(name: "thermostatFanMode", value: "auto", descriptionText: "Fan mode pinned to auto")
    //setSupportedThermostatFanModes(JsonOutput.toJson(["auto","high","mid","low"]))
    
    
    //def fanModes = ["auto", "cool", "emergency heat", "heat", "off"]
    //def modes = ["auto","circulate","on"]
    //def fanspeeds = ["low","medium-low","medium","medium-high","high","on","off","auto"]
    //sendEvent(name: "supportedThermostatFanModes", value: fanModes, descriptionText: "supportedThermostatFanModes set")    
	//sendEvent(name: "supportedThermostatModes", value: modes, descriptionText: "supportedThermostatModes set ")
	//sendEvent(name: "supportedFanSpeeds", value: fanspeeds , descriptionText: "supportedThermostatModes set ")

    
}


def parse(message) {
    def topic = interfaces.mqtt.parseMessage(message)
    def payload = new JsonSlurper().parseText(topic.payload)
    logger("trace", "parse(${payload})")

    parent.processMqttMessage(this, payload)
}

def mqttClientStatus(String message) {
    logger("debug", "mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        logger("error", "MQTT Error: ${message}")

        try {
            interfaces.mqtt.disconnect()
        }
        catch (e) {
        }
        mqttConnectUntilSuccessful()
    }
}

def processStateData(data) {
    logger("debug", "processStateData(${data})")

    if (!data) return

    // Process current state
    if (data.runState?.currentState) {
        def currentState = data.runState.currentState
        sendEvent(name: "currentState", value: currentState)
        
        if (logDescText) {
            log.info "${device.displayName} CurrentState: ${currentState}, Switch: ${switchState}"
        }
    }

    // Process job mode
    if (data.airConJobMode?.currentJobMode) {
        def jobMode = cleanEnumValue(data.airConJobMode.currentJobMode)
        sendEvent(name: "currentJobMode", value: jobMode)
        sendEvent(name: "thermostatMode", value: jobMode)
    
     log.info "job mode = " + jobMode
     
    }


    
    
    // Process operation modes
    if (data.operation?.airConOperationMode) {
        def opMode = cleanEnumValue(data.operation.airConOperationMode)
        sendEvent(name: "airConOperationMode", value: opMode)
       
        // Deriva switch do opMode que chegou (POWER_OFF → off, qualquer outro → on)
        // Nao usa currentState pois pode ser null quando runState nao vem no mesmo payload
        def rawOpMode = data.operation.airConOperationMode?.toString() ?: ""
        def switchState = (rawOpMode ==~ /(?i).*power_off.*/) ? 'off' : 'on'
        sendEvent(name: "switch", value: switchState)
        log.info "switchState = ${switchState} (opMode=${rawOpMode})"
    }

    // if (data.operation?.airCleanOperationMode) {
    //     def cleanMode = cleanEnumValue(data.operation.airCleanOperationMode)
    //     sendEvent(name: "airCleanOperationMode", value: cleanMode)
    // }

    // Process remote control
    // if (data.remoteControlEnable?.remoteControlEnabled != null) {
    //     def remoteEnabled = data.remoteControlEnable.remoteControlEnabled ? "enabled" : "disabled"
    //     sendEvent(name: "remoteControlEnabled", value: remoteEnabled)
    // }

    // Process temperature information
    // if (data.temperature?.currentTemperatureC != null) {
    //     def currentTemp = data.temperature.currentTemperatureC
    //     sendEvent(name: "currentTemperature", value: currentTemp, unit: "C")
    //     sendEvent(name: "temperature", value: currentTemp, unit: "C")
    // } else 
    if (data.temperature?.currentTemperature != null) {
        def currentTemp = data.temperature.currentTemperature
        sendEvent(name: "currentTemperature", value: currentTemp, unit: "F")
        sendEvent(name: "temperature", value: currentTemp, unit: "F")
    }

    // if (data.temperature?.targetTemperatureC != null) {
    //     def targetTemp = data.temperature.targetTemperatureC
    //     sendEvent(name: "targetTemperature", value: targetTemp, unit: "C")
    //     sendEvent(name: "coolingSetpoint", value: targetTemp, unit: "C")
    // } else 
    if (data.temperature?.targetTemperature != null) {
        def targetTemp = data.temperature.targetTemperature
        sendEvent(name: "targetTemperature", value: targetTemp, unit: "F")
        sendEvent(name: "coolingSetpoint", value: targetTemp, unit: "F")
    }

    // if (data.temperature?.minTargetTemperatureC != null) {
    //     sendEvent(name: "minTargetTemperature", value: data.temperature.minTargetTemperatureC)
    // } else 
    if (data.temperature?.minTargetTemperature != null) {
        sendEvent(name: "minTargetTemperature", value: data.temperature.minTargetTemperature)
    }

    // if (data.temperature?.maxTargetTemperatureC != null) {
    //     sendEvent(name: "maxTargetTemperature", value: data.temperature.maxTargetTemperatureC)
    // } else 
    if (data.temperature?.maxTargetTemperature != null) {
        sendEvent(name: "maxTargetTemperature", value: data.temperature.maxTargetTemperature)
    }

    // if (data.temperature?.unit) {
    //     sendEvent(name: "temperatureUnit", value: data.temperature.unit)
    // }

    // Process two set temperature
    // if (data.twoSetTemperature?.twoSetEnabled != null) {
    //     def twoSet = data.twoSetTemperature.twoSetEnabled ? "enabled" : "disabled"
    //     sendEvent(name: "twoSetEnabled", value: twoSet)
    // }

    // if (data.twoSetTemperatureInUnits?.heatTargetTemperatureC != null) {
    //     sendEvent(name: "heatTargetTemperature", value: data.twoSetTemperatureInUnits.heatTargetTemperatureC)
    //     sendEvent(name: "heatingSetpoint", value: data.twoSetTemperatureInUnits.heatTargetTemperatureC)
    // } else if (data.twoSetTemperatureInUnits?.heatTargetTemperatureF != null) {
    //     sendEvent(name: "heatTargetTemperature", value: data.twoSetTemperatureInUnits.heatTargetTemperatureF)
    //     sendEvent(name: "heatingSetpoint", value: data.twoSetTemperatureInUnits.heatTargetTemperatureF)
    // }

    // if (data.twoSetTemperatureInUnits?.coolTargetTemperatureC != null) {
    //     sendEvent(name: "coolTargetTemperature", value: data.twoSetTemperatureInUnits.coolTargetTemperatureC)
    //     sendEvent(name: "coolingSetpoint", value: data.twoSetTemperatureInUnits.coolTargetTemperatureC)
    // } else if (data.twoSetTemperatureInUnits?.coolTargetTemperatureF != null) {
    //     sendEvent(name: "coolTargetTemperature", value: data.twoSetTemperatureInUnits.coolTargetTemperatureF)
    //     sendEvent(name: "coolingSetpoint", value: data.twoSetTemperatureInUnits.coolTargetTemperatureF)
    // }

    // Process airflow information
    if (data.airFlow?.windStrength) {
        def windStrength = cleanEnumValue(data.airFlow.windStrength)
        sendEvent(name: "windStrength", value: windStrength)
    }

    // if (data.airFlow?.windStep != null) {
    //     sendEvent(name: "windStep", value: data.airFlow.windStep)
    // }

    // Process wind direction
    // if (data.windDirection?.rotateUpDown != null) {
    //     def rotateUpDown = data.windDirection.rotateUpDown ? "enabled" : "disabled"
    //     sendEvent(name: "rotateUpDown", value: rotateUpDown)
    // }

    // if (data.windDirection?.rotateLeftRight != null) {
    //     def rotateLeftRight = data.windDirection.rotateLeftRight ? "enabled" : "disabled"
    //     sendEvent(name: "rotateLeftRight", value: rotateLeftRight)
    // }

    // Process display light
    // if (data.display?.light != null) {
    //     def light = data.display.light ? "on" : "off"
    //     sendEvent(name: "light", value: light)
    // }

    // Process power save
    // if (data.powerSave?.powerSaveEnabled != null) {
    //     def powerSave = data.powerSave.powerSaveEnabled ? "enabled" : "disabled"
    //     sendEvent(name: "powerSaveEnabled", value: powerSave)
    // }

    // Process timer information
    if (data.timer?.relativeHourToStart != null) {
        sendEvent(name: "relativeHourToStart", value: data.timer.relativeHourToStart)
    }
    if (data.timer?.relativeMinuteToStart != null) {
        sendEvent(name: "relativeMinuteToStart", value: data.timer.relativeMinuteToStart)
    }
    if (data.timer?.relativeHourToStop != null) {
        sendEvent(name: "relativeHourToStop", value: data.timer.relativeHourToStop)
    }
    if (data.timer?.relativeMinuteToStop != null) {
        sendEvent(name: "relativeMinuteToStop", value: data.timer.relativeMinuteToStop)
    }
    // if (data.timer?.absoluteHourToStart != null) {
    //     sendEvent(name: "absoluteHourToStart", value: data.timer.absoluteHourToStart)
    // }
    // if (data.timer?.absoluteMinuteToStart != null) {
    //     sendEvent(name: "absoluteMinuteToStart", value: data.timer.absoluteMinuteToStart)
    // }
    // if (data.timer?.absoluteHourToStop != null) {
    //     sendEvent(name: "absoluteHourToStop", value: data.timer.absoluteHourToStop)
    // }
    // if (data.timer?.absoluteMinuteToStop != null) {
    //     sendEvent(name: "absoluteMinuteToStop", value: data.timer.absoluteMinuteToStop)
    // }

    // Process sleep timer
    // if (data.sleepTimer?.relativeHourToStop != null) {
    //     sendEvent(name: "sleepRelativeHourToStop", value: data.sleepTimer.relativeHourToStop)
    // }
    // if (data.sleepTimer?.relativeMinuteToStop != null) {
    //     sendEvent(name: "sleepRelativeMinuteToStop", value: data.sleepTimer.relativeMinuteToStop)
    // }

    // Process air quality sensor data
    // if (data.airQualitySensor?.monitoringEnabled != null) {
    //     def monitoring = data.airQualitySensor.monitoringEnabled ? "enabled" : "disabled"
    //     sendEvent(name: "airQualityMonitoringEnabled", value: monitoring)
    // }

    // if (data.airQualitySensor?.PM1 != null) {
    //     sendEvent(name: "pm1", value: data.airQualitySensor.PM1)
    // }

    // if (data.airQualitySensor?.PM2 != null) {
    //     sendEvent(name: "pm2", value: data.airQualitySensor.PM2)
    // }

    // if (data.airQualitySensor?.PM10 != null) {
    //     sendEvent(name: "pm10", value: data.airQualitySensor.PM10)
    // }

    // if (data.airQualitySensor?.odorLevel) {
    //     def odorLevel = cleanEnumValue(data.airQualitySensor.odorLevel)
    //     sendEvent(name: "odorLevel", value: odorLevel)
    // }

    // if (data.airQualitySensor?.humidity != null) {
    //     sendEvent(name: "humidity", value: data.airQualitySensor.humidity, unit: "%")
    // }

    // if (data.airQualitySensor?.totalPollutionLevel) {
    //     def pollutionLevel = cleanEnumValue(data.airQualitySensor.totalPollutionLevel)
    //     sendEvent(name: "totalPollutionLevel", value: pollutionLevel)
    // }

    // Process filter information
    // if (data.filterInfo?.filterRemainPercent != null) {
    //     sendEvent(name: "filterRemainPercent", value: data.filterInfo.filterRemainPercent, unit: "%")
    // }

    if (data.filterInfo?.usedTime != null) {
        sendEvent(name: "filterUsedTime", value: data.filterInfo.usedTime, unit: "hours")
    }

    if (data.filterInfo?.filterLifetime != null) {
        sendEvent(name: "filterLifetime", value: data.filterInfo.filterLifetime, unit: "hours")
    }

    // Process error state
    if (data.error) {
        def errorState = cleanEnumValue(data.error)
        sendEvent(name: "error", value: errorState)
    }
}

def heat() {
    log.debug "Thermostat heat() called"
    setAirConJobMode("HEAT")
    sendEvent(name: "thermostatMode", value: "heat")
    sendEvent(name: "currentJobMode", value: "HEAT")   // <- ADD
    
}

def cool() {
    log.debug "Thermostat cool() called"
    setAirConJobMode("COOL")
    sendEvent(name: "thermostatMode", value: "cool")
    sendEvent(name: "currentJobMode", value: "COOL")   // <- ADD
    
}

def getDeviceProfile() {
    logger("debug", "getDeviceProfile()")
    parent.getDeviceProfile(getDeviceId())
}

def start() {
    logger("debug", "start()")
    def deviceId = getDeviceId()
    def command = [
        operation: [
            airConOperationMode: "POWER_ON"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "airConOperationMode", value: "On")
}

def stop() {
    logger("debug", "stop()")
    def deviceId = getDeviceId()
    def command = [
        operation: [
            airConOperationMode: "POWER_OFF"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "airConOperationMode", value: "Off")
}

// def powerOff() {
//     logger("debug", "powerOff()")
//     def deviceId = getDeviceId()
//     def command = [
//         operation: [
//             airConOperationMode: "POWER_OFF"
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

def on() {
    start()
}

def off() {
    stop()
}

def setAirConOperationMode(mode) {
    logger("debug", "setAirConOperationMode(${mode})")
    def deviceId = getDeviceId()
    def command = [
        operation: [
            airConOperationMode: mode
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

def setAirConJobMode(mode) {
    logger("debug", "setAirConJobMode(${mode})")
    def deviceId = getDeviceId()
    def command = [
        airConJobMode: [
            currentJobMode: mode
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

// def setAirCleanOperationMode(mode) {
//     logger("debug", "setAirCleanOperationMode(${mode})")
//     def deviceId = getDeviceId()
//     def command = [
//         operation: [
//             airCleanOperationMode: mode
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

//TEST TARGET TEMP VH

def setTargetTemperature(temperature) {
    logger("debug", "setTargetTemperature(${temperature})")
     def deviceId = getDeviceId()
     def command = [
         temperature: [
             targetTemperature: temperature,
             unit: isFahrenheit ? "F" : "C"
         ]
     ]
     parent.sendDeviceCommand(deviceId, command)
 }

// def setHeatTargetTemperature(temperature) {
//     logger("debug", "setHeatTargetTemperature(${temperature})")
//     def deviceId = getDeviceId()
//     def command = [
//         twoSetTemperatureInUnits: [
//             heatTargetTemperatureC: temperature
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

def setCoolingSetpoint(temperature) {
    logger("debug", "setCoolingSetpoint(${temperature})")
    def deviceId = getDeviceId()
    def command = [
        temperature: [
            coolTargetTemperature: temperature,
            unit: isFahrenheit ? "F" : "C"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

def setWindStrength(strength) {
    logger("debug", "setWindStrength(${strength})")
    def deviceId = getDeviceId()
    def command = [
        airFlow: [
            windStrength: strength
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}


def setLight(state) {
     log.debug "setlightc= " + state
     def deviceId = getDeviceId()
     def command = [
         display: [
             light: state
         ]
     ]
     parent.sendDeviceCommand(deviceId, command)
 }


// def setWindStep(step) {
//     logger("debug", "setWindStep(${step})")
//     def deviceId = getDeviceId()
//     def command = [
//         airFlow: [
//             windStep: step
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }


// def setRotateUpDown(enabled) {
//     logger("debug", "setRotateUpDown(${enabled})")
//     def deviceId = getDeviceId()
//     def command = [
//         windDirection: [
//             rotateUpDown: enabled == "enabled" || enabled == true
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

// def setRotateLeftRight(enabled) {
//     logger("debug", "setRotateLeftRight(${enabled})")
//     def deviceId = getDeviceId()
//     def command = [
//         windDirection: [
//             rotateLeftRight: enabled == "enabled" || enabled == true
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

// def setPowerSave(enabled) {
//     logger("debug", "setPowerSave(${enabled})")
//     def deviceId = getDeviceId()
//     def command = [
//         powerSave: [
//             powerSaveEnabled: enabled == "enabled" || enabled == true
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

// def setTwoSetEnabled(enabled) {
//     logger("debug", "setTwoSetEnabled(${enabled})")
//     def deviceId = getDeviceId()
//     def command = [
//         twoSetTemperature: [
//             twoSetEnabled: enabled == "enabled" || enabled == true
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }


def setDelayStart(minutes) {
    logger("debug", "setDelayStart(${minutes})")
    def deviceId = getDeviceId()
    def command = [
        timer: [
            relativeHourToStart: minutes.intdiv(60),
            relativeMinuteToStart: minutes % 60,
            relativeStartTimer: "SET"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

def setDelayStop(minutes) {
    logger("debug", "setDelayStop(${minutes})")
    def deviceId = getDeviceId()
    def command = [
        timer: [
            relativeHourToStop: minutes.intdiv(60),
            relativeMinuteToStop: minutes % 60,
            relativeStartTimer: "SET"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

def unsetStopTimer() {
    logger("debug", "unsetStopTimer()")
    def deviceId = getDeviceId()
    def command = [
        timer: [
            relativeStartTimer: "UNSET"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

def unsetStartTimer() {
    logger("debug", "unsetStartTimer()")
    def deviceId = getDeviceId()
    def command = [
        timer: [
            relativeStartTimer: "UNSET"
        ]
    ]
    parent.sendDeviceCommand(deviceId, command)
}

// def setAbsoluteStart(hour, minute) {
//     logger("debug", "setAbsoluteStart(${hour}, ${minute})")
//     def deviceId = getDeviceId()
//     def command = [
//         timer: [
//             absoluteHourToStart: hour,
//             absoluteMinuteToStart: minute
//         ]
//     ]
//     parent.sendDeviceCommand(deviceId, command)
// }

def getDeviceId() {
    return device.deviceNetworkId.replace("thinqconnect:", "")
}

def getDeviceDetails() {
    def deviceId = getDeviceId()
    return parent.state.foundDevices.find { it.id == deviceId }
}

def cleanEnumValue(value) {
    if (value == null) return ""
    
    // Convert enum values to readable format
    return value.toString()
        .replaceAll(/^[A-Z_]+_/, "")  // Remove prefix
        .replaceAll(/_/, " ")         // Replace underscores with spaces
        .toLowerCase()                // Convert to lowercase
        .split(' ')                   // Split into words
        .collect { it.capitalize() }  // Capitalize each word
        .join(' ')                    // Join back together
}

def convertSecondsToTime(int sec) {
    if (sec <= 0) return "00:00"
    
    long hours = sec / 3600
    long minutes = (sec % 3600) / 60
    
    return String.format("%02d:%02d", hours, minutes)
}

/**
* @param level Level to log at, see LOG_LEVELS for options
* @param msg Message to log
*/
private logger(level, msg) {
    if (level && msg) {
        Integer levelIdx = LOG_LEVELS.indexOf(level)
        Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
        if (setLevelIdx < 0) {
            setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
        }
        if (levelIdx <= setLevelIdx) {
            log."${level}" "${device.displayName} ${msg}"
        }
    }
}


/*
 * HomeKit Bridge / Thermostat compatibility wrappers
 * (Adds missing Thermostat methods without changing existing behavior)
 */

// HomeKit commonly calls this
def setThermostatMode(String mode) {
    logger("debug", "setThermostatMode(${mode})")

    if (!mode) return
    def m = mode.toLowerCase()

    switch (m) {
        case "off":
            stop()
            break

        case "cool":
            start()
            // Use existing job modes already supported by driver UI
            setAirConJobMode("COOL")
            break

        case "heat":
            // If your LG model supports HEAT via API, you can enable it here.
            // For now, map to COOL to avoid breaking anything (or just start()).
            start()
            // setAirConJobMode("HEAT")  // only if your profile supports it
            setAirConJobMode("HEAT")        
            break

        case "auto":
            start()
            // If your profile supports AUTO, use it. Otherwise keep device on.
            // setAirConJobMode("AUTO")
            break

        case "dry":
        case "dehumidify":
            start()
            setAirConJobMode("AIR_DRY")
            break

        case "fan":
        case "fanonly":
        case "fan only":
            start()
            setAirConJobMode("FAN")
            break

        default:
            // Fallback: at least power on
            start()
            break
    }

    // Optional: emit thermostatMode so HomeKit UI stays in sync
    // (doesn't change device behavior, only state reporting)
    try {
        sendEvent(name: "thermostatMode", value: m)
    } catch (ignored) {}
}

// Some integrations call these legacy fan helpers
def fanOn() {
    logger("debug", "fanOn()")
    setThermostatFanMode("on")
}

def fanAuto() {
    logger("debug", "fanAuto()")
    setThermostatFanMode("auto")
}

// HomeKit may call this too
def setThermostatFanMode(String fanMode) {
    logger("debug", "setThermostatFanMode(${fanMode})")

    if (!fanMode) return
    def f = fanMode.toLowerCase()

    // Minimal mapping that won't disrupt current driver logic:
    // - "on" => ensure unit on + FAN job mode
    // - "auto" => keep unit on, but don't force FAN mode (leave current job)
    switch (f) {
        case "on":
            start()
            break
        case "auto":
            // Do not change job mode; just ensure it is on if needed
            start()
            break
        case "circulate":
            start()
            // You could decide to map circulate to FAN as well:
            // setAirConJobMode("FAN")
            break
        default:
            break
    }

    // Optional: emit thermostatFanMode
    try {
        sendEvent(name: "thermostatFanMode", value: f)
    } catch (ignored) {}
}

// Optional helper: some bridges call setThermostatOperatingState directly (rare)
def setThermostatOperatingState(String state) {
    logger("debug", "setThermostatOperatingState(${state})")
    // Not used to control the device; only to satisfy callers if they attempt it.
    try {
        sendEvent(name: "thermostatOperatingState", value: state)
    } catch (ignored) {}
}
