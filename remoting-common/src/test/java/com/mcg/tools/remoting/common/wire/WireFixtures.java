package com.mcg.tools.remoting.common.wire;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;

/** Fixed types and values for the wire-format golden files (design D2 "Fixtures"). */
public final class WireFixtures {

	private WireFixtures() {
	}

	@RemoteEndpoint(app = "test", name = "wire")
	public interface WireService {
		Sample all(Date date, Instant instant, Locale locale, byte[] bytes, Color color, Sample sample, Map<String, Object> map);

		void fire(String text, Integer number);
	}

	public enum Color {
		RED, GREEN
	}

	public static class Sample {
		private String name;
		private Date when;
		private Instant at;
		private Color color;
		private List<Sample> children;
		private Map<String, String> tags;

		public Sample() {
		}

		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
		public Date getWhen() { return when; }
		public void setWhen(Date when) { this.when = when; }
		public Instant getAt() { return at; }
		public void setAt(Instant at) { this.at = at; }
		public Color getColor() { return color; }
		public void setColor(Color color) { this.color = color; }
		public List<Sample> getChildren() { return children; }
		public void setChildren(List<Sample> children) { this.children = children; }
		public Map<String, String> getTags() { return tags; }
		public void setTags(Map<String, String> tags) { this.tags = tags; }

		@Override
		public boolean equals(Object o) {
			return o instanceof Sample s && Objects.equals(name, s.name) && Objects.equals(when, s.when)
					&& Objects.equals(at, s.at) && color == s.color && Objects.equals(children, s.children)
					&& Objects.equals(tags, s.tags);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, when, at, color, children, tags);
		}
	}

	/** 2026-11-16T12:00:00Z */
	public static final Date DATE = new Date(1794830400000L);
	public static final Instant INSTANT = DATE.toInstant();
	public static final Locale LOCALE = Locale.GERMANY;
	public static final byte[] BYTES = { 1, 2, 3, (byte) 255 };
	public static final Color COLOR = Color.GREEN;
	public static final Sample SAMPLE = sample("parent", List.of(sample("child", null)));
	public static final Map<String, Object> MAP = map();

	/** Arguments for {@link WireService#all}, in parameter order. */
	public static Object[] allArgs() {
		return new Object[] { DATE, INSTANT, LOCALE, BYTES, COLOR, SAMPLE, MAP };
	}

	/** A fresh exception with a fixed one-frame stack trace, so the encoded stack trace is stable. */
	public static IllegalStateException exception() {
		IllegalStateException e = new IllegalStateException("boom");
		e.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.Remote", "call", "Remote.java", 42) });
		return e;
	}

	private static Sample sample(String name, List<Sample> children) {
		Sample s = new Sample();
		s.setName(name);
		s.setWhen(DATE);
		s.setAt(INSTANT);
		s.setColor(COLOR);
		s.setChildren(children);
		s.setTags(Map.of("k", "v"));
		return s;
	}

	// ponytail: LinkedHashMap only so the generated file has a stable, readable key order; tests compare as trees.
	private static Map<String, Object> map() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("s", "x");
		m.put("n", 42);
		m.put("b", true);
		m.put("l", List.of(1, "two"));
		m.put("nested", Map.of("k", "v"));
		return m;
	}
}
