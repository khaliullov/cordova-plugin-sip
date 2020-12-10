class LogHelper {
  constructor(context) {
    this.context = context;
    this.events = this.context.requireCordovaModule('cordova-common').events;
  }
  debug(...args) {
    this.events.emit('verbose', ['[CordovaSip]', ...args].join(' '));
  }
  warn(...args) {
    this.events.emit('warn', ['[CordovaSip]', ...args].join(' '));
  }
  log(...args) {
    this.events.emit('log', ['[CordovaSip]', ...args].join(' '));
  }
}

module.exports = LogHelper;