package com.mcg.tools.remoting.common.wire;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;

/** Fixed types and values for the wire-format golden files (design D2 "Fixtures"). */
public final class WireFixtures {

	private WireFixtures() {
	}

	@RemoteEndpoint(app = "test", name = "wire")
	public interface WireService {
		Sample all(Date date, Instant instant, Locale locale, byte[] bytes, Color color, Sample sample, Map<String, Object> map);

		void fire(String text, Integer number);

		/** Types added for the Jackson 3 port (0.4.0); recorded with the 0.3.x Jackson 2 codec. */
		More more(More more, BigDecimal amount, long big, Optional<String> optional, Level level, Point point, Map<Date, String> byDate);
	}

	/** An enum whose toString() differs from its name. */
	public enum Level {
		LOW, HIGH;

		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public record Point(int x, String label) {
	}

	/** Like dbutils' BaseEntityV2: the id comes from a generic superclass. */
	public static class Entity<T> {
		private T id;

		public T getId() { return id; }
		public void setId(T id) { this.id = id; }
	}

	public static class More extends Entity<String> {
		private BigDecimal amount;
		private long big;
		private Long boxed;
		private LocalDate day;
		private LocalDateTime local;
		private OffsetDateTime offset;
		private Duration duration;
		private Level level;
		private Point point;
		private Map<Long, String> byId;
		private Map<String, Object> props;

		public More() {
		}

		public BigDecimal getAmount() { return amount; }
		public void setAmount(BigDecimal amount) { this.amount = amount; }
		public long getBig() { return big; }
		public void setBig(long big) { this.big = big; }
		public Long getBoxed() { return boxed; }
		public void setBoxed(Long boxed) { this.boxed = boxed; }
		public LocalDate getDay() { return day; }
		public void setDay(LocalDate day) { this.day = day; }
		public LocalDateTime getLocal() { return local; }
		public void setLocal(LocalDateTime local) { this.local = local; }
		public OffsetDateTime getOffset() { return offset; }
		public void setOffset(OffsetDateTime offset) { this.offset = offset; }
		public Duration getDuration() { return duration; }
		public void setDuration(Duration duration) { this.duration = duration; }
		public Level getLevel() { return level; }
		public void setLevel(Level level) { this.level = level; }
		public Point getPoint() { return point; }
		public void setPoint(Point point) { this.point = point; }
		public Map<Long, String> getById() { return byId; }
		public void setById(Map<Long, String> byId) { this.byId = byId; }
		public Map<String, Object> getProps() { return props; }
		public void setProps(Map<String, Object> props) { this.props = props; }

		@Override
		public boolean equals(Object o) {
			return o instanceof More m && Objects.equals(getId(), m.getId()) && Objects.equals(amount, m.amount) && big == m.big
					&& Objects.equals(boxed, m.boxed) && Objects.equals(day, m.day) && Objects.equals(local, m.local)
					&& Objects.equals(offset, m.offset) && Objects.equals(duration, m.duration) && level == m.level
					&& Objects.equals(point, m.point) && Objects.equals(byId, m.byId) && Objects.equals(props, m.props);
		}

		@Override
		public int hashCode() {
			return Objects.hash(getId(), amount, big, boxed, day, local, offset, duration, level, point, byId, props);
		}
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

	/** 2^53 + 1: not representable as a double */
	public static final long BIG = 9007199254740993L;
	public static final BigDecimal AMOUNT = new BigDecimal("1234.50");
	public static final More MORE = more();

	/** Arguments for {@link WireService#more}, in parameter order. */
	public static Object[] moreArgs() {
		Map<Date, String> byDate = new LinkedHashMap<>();
		byDate.put(DATE, "d");
		return new Object[] { MORE, AMOUNT, BIG, Optional.of("present"), Level.HIGH, new Point(3, "p"), byDate };
	}

	private static More more() {
		More m = new More();
		m.setId("guid-1");
		m.setAmount(AMOUNT);
		m.setBig(BIG);
		m.setBoxed(null);
		m.setDay(LocalDate.of(2026, 11, 16));
		m.setLocal(LocalDateTime.of(2026, 11, 16, 12, 0, 1, 500_000_000));
		m.setOffset(OffsetDateTime.of(2026, 11, 16, 14, 0, 0, 0, ZoneOffset.ofHours(2)));
		m.setDuration(Duration.ofMinutes(90));
		m.setLevel(Level.LOW);
		m.setPoint(new Point(1, "one"));
		Map<Long, String> byId = new LinkedHashMap<>();
		byId.put(BIG, "big");
		m.setById(byId);
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("date", DATE);
		props.put("big", BIG);
		props.put("amount", AMOUNT);
		props.put("locale", LOCALE);
		m.setProps(props);
		return m;
	}

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
