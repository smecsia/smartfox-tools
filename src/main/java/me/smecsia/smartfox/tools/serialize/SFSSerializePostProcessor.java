package me.smecsia.smartfox.tools.serialize;

import com.smartfoxserver.v2.entities.data.ISFSObject;
import me.smecsia.smartfox.tools.common.TransportObject;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
 * @author Ilya Sadykov
 *         Date: 16.11.12
 *         Time: 1:18
 */
public interface SFSSerializePostProcessor {
    public <T extends TransportObject> void process(final ISFSObject result, final T sourceObject);
}
