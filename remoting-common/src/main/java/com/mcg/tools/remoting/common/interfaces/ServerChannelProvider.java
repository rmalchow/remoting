package com.mcg.tools.remoting.common.interfaces;

import com.mcg.tools.remoting.common.ExportedService;

public interface ServerChannelProvider {

	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService);

}
