package me.smecsia.smartfox.tools;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;
import me.smecsia.smartfox.tools.common.BasicHandler;
import me.smecsia.smartfox.tools.error.UnauthorizedException;
import me.smecsia.smartfox.tools.service.BasicAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Ilya Sadykov
 *         Date: 19.09.12
 *         Time: 14:32
 */
public abstract class AbstractClientRequestHandler extends BaseClientRequestHandler implements BasicHandler {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected AbstractClientRequestHandler() {
        super();
    }

    @Override
    public SFSExtension getParentExtension() {
        return super.getParentExtension();
    }

    public abstract void doHandle(User user, ISFSObject isfsObject);

    @Override
    public final void handleClientRequest(User user, ISFSObject isfsObject) {
        try {
            BasicAuthService.checkAuthIfRequired(this, user);
            doHandle(user, isfsObject);
        } catch (UnauthorizedException ua) {
            logger.error("User unauthorized: " + ua.getMessage());
        }
    }
}
