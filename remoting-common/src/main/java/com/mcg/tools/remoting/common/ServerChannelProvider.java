package com.mcg.tools.remoting.common;

import com.mcg.tools.remoting.common.io.ServerChannel;

public interface ServerChannelProvider {

	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService);

}
