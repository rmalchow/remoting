package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.List;

public class RemotingExportService {
	
	private List<ExportedService> exportedServices = new ArrayList<>();

	public boolean add(ExportedService s) {
		return exportedServices.add(s);
	}

}
