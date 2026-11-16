package com.mcg.tools.remoting.common.regression;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.common.wire.WireFixtures.Color;
import com.mcg.tools.remoting.common.wire.WireFixtures.Sample;

@RemotingEndpoint(RegService.class)
public class RegServiceImpl implements RegService {

	static Sample sample(String name) {
		Sample s = new Sample();
		s.setName(name);
		s.setColor(Color.RED);
		return s;
	}

	@Override
	public List<Sample> samples() {
		return List.of(sample("a"), sample("b"));
	}

	@Override
	public Map<String, Sample> sampleMap() {
		Map<String, Sample> m = new LinkedHashMap<>();
		m.put("x", sample("x"));
		return m;
	}

	@Override
	public Set<Color> colors() {
		return Set.of(Color.RED, Color.GREEN);
	}

	@Override
	public Box<Sample> box() {
		Box<Sample> b = new Box<>();
		b.value = sample("v");
		b.more = List.of(sample("m"));
		return b;
	}

	@Override
	public void fail() {
		throw new IllegalStateException("boom");
	}

	@Override
	public String find(String s) {
		return "find(String):" + s;
	}

	@Override
	public String find(int i) {
		return "find(int):" + i;
	}

	@Override
	public String put(Date d) {
		return "put(Date)";
	}

	@Override
	public String put(Sample s) {
		return "put(Sample)";
	}

	@Override
	public Duration duration(Duration d) {
		return d;
	}

	@Override
	public Optional<String> optional(Optional<String> o) {
		return o;
	}

	@Override
	public LocalDate date(LocalDate d) {
		return d;
	}

	@Override
	public String ping() {
		return "pong";
	}
}
