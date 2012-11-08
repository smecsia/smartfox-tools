package me.smecsia.smartfox.tools;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.exceptions.SFSException;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;
import me.smecsia.smartfox.tools.common.BasicHandler;
import me.smecsia.smartfox.tools.error.UnauthorizedException;
import me.smecsia.smartfox.tools.service.BasicAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Ilya Sadykov
 *         Date: 20.09.12
 *         Time: 15:54
 */
public abstract class AbstractServerEventHandler extends BaseServerEventHandler implements BasicHandler {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    public AbstractServerEventHandler() {
        super();
    }

    @Override
    public SFSExtension getParentExtension() {
        return super.getParentExtension();
    }

    @Override
    public final void handleServerEvent(ISFSEvent isfsEvent) throws SFSException {
        try {
            BasicAuthService.checkAuthIfRequired(this, (User) isfsEvent.getParameter(SFSEventParam.USER));
            doHandle(isfsEvent);
        } catch (UnauthorizedException ua) {
            logger.error("User unauthorized: " + ua.getMessage());
        }
    }

    public abstract void doHandle(ISFSEvent isfsEvent) throws SFSException;
}
