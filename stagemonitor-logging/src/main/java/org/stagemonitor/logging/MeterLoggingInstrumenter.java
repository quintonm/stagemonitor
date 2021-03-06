package org.stagemonitor.logging;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javassist.CtClass;
import javassist.CtMethod;
import org.stagemonitor.core.util.ClassUtils;
import org.stagemonitor.core.instrument.StagemonitorJavassistInstrumenter;

/**
 * Tracks the rate of calls to a logger.
 * <p/>
 * Currently has support for Logback, slf4j's simple logger and JDK14LoggerAdapter, log4j 1.x and 2.x
 */
public class MeterLoggingInstrumenter extends StagemonitorJavassistInstrumenter {

	private Set<String> methodsToInstrument = new HashSet<String>() {{
		add("trace");
		add("debug");
		add("info");
		add("warn");
		add("error");
		add("fatal");
	}};

	private static Set<String> loggerImplementations = new HashSet<String>() {{
		add("ch/qos/logback/classic/Logger");
		add("org/slf4j/impl/SimpleLogger");
		add("org/apache/logging/log4j/spi/AbstractLogger");
		add("org/apache/log4j/Logger");
		add("org/slf4j/impl/JDK14LoggerAdapter");
	}};

	@Override
	public boolean isIncluded(String className) {
		return loggerImplementations.contains(className);
	}

	@Override
	public void transformClass(CtClass ctClass, ClassLoader loader) throws Exception {
		for (CtMethod ctMethod : ctClass.getMethods()) {
			if (methodsToInstrument.contains(ctMethod.getName())) {
				ctMethod.insertBefore("org.stagemonitor.core.Stagemonitor.getMetricRegistry()" +
						".meter(\"logging." + ctMethod.getName() + "\").mark();");
			}
		}
	}

	/**
	 * The logger implementations are already loaded before the agent is initialized so they have to be
	 * retransformed afterwards
	 *
	 * @param inst the instrumentation
	 * @return the classes to retransform
	 */
	public static Collection<Class<?>> getClassesToRetransform(Instrumentation inst) {
		Set<Class> allLoadedClasses = new HashSet<Class>(Arrays.asList(inst.getAllLoadedClasses()));
		Set<Class<?>> result = new HashSet<Class<?>>();
		for (String loggerImplementation : loggerImplementations) {
			addClassIfLoaded(result, loggerImplementation.replace("/", "."), allLoadedClasses);
		}

		return result;
	}

	private static void addClassIfLoaded(Set<Class<?>> result, String className, Set<Class> allLoadedClasses) {
		final Class<?> clazz = ClassUtils.forNameOrNull(className);
		if (allLoadedClasses.contains(clazz)) {
			result.add(clazz);
		}
	}
}
