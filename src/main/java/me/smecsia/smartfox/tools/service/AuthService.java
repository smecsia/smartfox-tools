package me.smecsia.smartfox.tools.service;

import com.smartfoxserver.v2.entities.User;
import me.smecsia.smartfox.tools.error.UnauthorizedException;

/**
 * @author Ilya Sadykov
 *         Date: 06.11.12
 *         Time: 18:46
 */
public interface AuthService {
    void check(User user) throws UnauthorizedException;
}
