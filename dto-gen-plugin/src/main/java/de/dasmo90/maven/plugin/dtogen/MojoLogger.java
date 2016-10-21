package de.dasmo90.maven.plugin.dtogen;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.Logger;
import org.slf4j.Marker;

public class MojoLogger {

	private Log log;

	public MojoLogger(Log log) {
		this.log = log;
	}

	public boolean isDebugEnabled() {
		return this.log.isDebugEnabled();
	}

	public void debug(String msg) {
		if(this.log.isDebugEnabled()) {
			this.log.debug(msg);
		}
	}

	public void debug(String msg, Throwable t) {
		if(this.log.isDebugEnabled()) {
			this.log.debug(msg, t);
		}
	}

	public boolean isInfoEnabled() {
		return this.log.isInfoEnabled();
	}

	public void info(String msg) {
		if(this.log.isInfoEnabled()) {
			this.log.info(msg);
		}
	}

	public void info(String msg, Throwable t) {
		if(this.log.isInfoEnabled()) {
			this.log.info(msg, t);
		}
	}

	public boolean isWarnEnabled() {
		return this.log.isWarnEnabled();
	}

	public void warn(String msg) {
		if(this.log.isWarnEnabled()) {
			this.log.warn(msg);
		}
	}

	public void warn(String msg, Throwable t) {
		if(this.log.isWarnEnabled()) {
			this.log.warn(msg, t);
		}
	}

	public boolean isErrorEnabled() {
		return this.log.isErrorEnabled();
	}

	public void error(String msg) {
		if(this.log.isErrorEnabled()) {
			this.log.error(msg);
		}
	}

	public void error(String msg, Throwable t) {
		if(this.log.isErrorEnabled()) {
			this.log.error(msg, t);
		}
	}
}
