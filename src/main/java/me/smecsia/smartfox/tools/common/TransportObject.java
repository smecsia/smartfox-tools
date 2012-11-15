package me.smecsia.smartfox.tools.common;

import com.smartfoxserver.v2.entities.data.ISFSObject;

/**
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 15:28
 */
public interface TransportObject {
    public ISFSObject toSFSObject();
    public void updateFromSFSObject(ISFSObject obj);

    String toJson();
}
