package me.smecsia.smartfox.tools.common;

import com.smartfoxserver.v2.entities.data.ISFSObject;
import me.smecsia.smartfox.tools.util.SFSObjectUtil;

/**
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 15:30
 */
public abstract class AbstractTransportObject implements TransportObject {

    @Override
    public ISFSObject toSFSObject() {
        return SFSObjectUtil.serialize(this);
    }

    @Override
    public void updateFromSFSObject(ISFSObject obj) {
        SFSObjectUtil.deserialize(this, obj);
    }
}
