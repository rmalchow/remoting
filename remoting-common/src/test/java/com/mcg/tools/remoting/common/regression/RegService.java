package com.mcg.tools.remoting.common.regression;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.common.wire.WireFixtures.Color;
import com.mcg.tools.remoting.common.wire.WireFixtures.Sample;

@RemoteEndpoint(app = "test", name = "reg")
public interface RegService {

	/** nested generic DTO */
	class Box<T> {
		public T value;
		public List<T> more;
	}

	List<Sample> samples();

	Map<String, Sample> sampleMap();

	Set<Color> colors();

	Box<Sample> box();

	void fail();

	String find(String s);

	String find(int i);

	String put(Date d);

	String put(Sample s);

	Duration duration(Duration d);

	Optional<String> optional(Optional<String> o);

	LocalDate date(LocalDate d);

	String ping();
}
