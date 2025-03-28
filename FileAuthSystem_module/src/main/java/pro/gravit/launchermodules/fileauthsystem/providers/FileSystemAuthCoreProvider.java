package pro.gravit.launchermodules.fileauthsystem.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.password.AuthPlainPassword;
import pro.gravit.launchermodules.fileauthsystem.FileAuthSystemConfig;
import pro.gravit.launchermodules.fileauthsystem.FileAuthSystemModule;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.auth.AuthException;
import pro.gravit.launchserver.auth.core.AuthCoreProvider;
import pro.gravit.launchserver.auth.core.User;
import pro.gravit.launchserver.auth.core.UserSession;
import pro.gravit.launchserver.manangers.AuthManager;
import pro.gravit.launchserver.socket.response.auth.AuthResponse;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.util.UUID;

public class FileSystemAuthCoreProvider extends AuthCoreProvider {
    private transient Logger logger = LogManager.getLogger();
    private FileAuthSystemModule module;

    @Override
    public User getUserByUsername(String username) {
        return module.getUser(username);
    }

    @Override
    public User getUserByUUID(UUID uuid) {
        return module.getUser(uuid);
    }

    @Override
    public UserSession getUserSessionByOAuthAccessToken(String accessToken) throws OAuthAccessTokenExpired {
        FileAuthSystemConfig config = module.jsonConfigurable.getConfig();
        if (!config.memoryOAuth) return null;
        FileAuthSystemModule.UserSessionEntity session = module.getSessionByAccessToken(accessToken);
        if (session == null) return null;
        if (session.expireMillis != 0 && session.expireMillis < System.currentTimeMillis())
            throw new OAuthAccessTokenExpired();
        return session;
    }

    @Override
    public AuthManager.AuthReport refreshAccessToken(String refreshToken, AuthResponse.AuthContext context) {
        FileAuthSystemConfig config = module.jsonConfigurable.getConfig();
        if (!config.memoryOAuth) return null;
        FileAuthSystemModule.UserSessionEntity session = module.getSessionByRefreshToken(refreshToken);
        if (session == null) return null;
        session.refreshToken = SecurityHelper.randomStringToken();
        session.accessToken = SecurityHelper.randomStringToken();
        if (config.oauthTokenExpire != 0) {
            session.update(config.oauthTokenExpire);
        }
        return AuthManager.AuthReport.ofOAuth(session.accessToken, session.refreshToken, config.oauthTokenExpire);
    }

    @Override
    public void verifyAuth(AuthResponse.AuthContext context) throws AuthException {
        // None
    }

    @Override
    public PasswordVerifyReport verifyPassword(User user, AuthRequest.AuthPasswordInterface password) {
        FileAuthSystemModule.UserEntity entity = (FileAuthSystemModule.UserEntity) user;
        if (!(password instanceof AuthPlainPassword)) {
            return PasswordVerifyReport.FAILED;
        }
        AuthPlainPassword plainPassword = (AuthPlainPassword) password;
        if (entity.verifyPassword(plainPassword.password)) {
            return new PasswordVerifyReport(true);
        }
        return PasswordVerifyReport.FAILED;
    }

    @Override
    public AuthManager.AuthReport createOAuthSession(User user, AuthResponse.AuthContext context, PasswordVerifyReport report, boolean minecraftAccess) throws IOException {
        FileAuthSystemConfig config = module.jsonConfigurable.getConfig();
        if (config.memoryOAuth) {
            FileAuthSystemModule.UserSessionEntity entity = new FileAuthSystemModule.UserSessionEntity((FileAuthSystemModule.UserEntity) user);
            module.addNewSession(entity);
            if (config.oauthTokenExpire != 0) {
                entity.update(config.oauthTokenExpire);
            }
            if (minecraftAccess) {
                String minecraftAccessToken = SecurityHelper.randomStringToken();
                ((FileAuthSystemModule.UserEntity) user).accessToken = minecraftAccessToken;
                return AuthManager.AuthReport.ofOAuthWithMinecraft(minecraftAccessToken, entity.accessToken, entity.refreshToken, config.oauthTokenExpire);
            }
            return AuthManager.AuthReport.ofOAuth(entity.accessToken, entity.refreshToken, config.oauthTokenExpire);
        } else {
            return AuthManager.AuthReport.ofMinecraftAccessToken(minecraftAccess ? SecurityHelper.randomStringToken() : null);
        }
    }

    @Override
    public void init(LaunchServer server) {
        module = server.modulesManager.getModule(FileAuthSystemModule.class);
    }

    @Override
    protected boolean updateServerID(User user, String serverID) throws IOException {
        FileAuthSystemModule.UserEntity entity = (FileAuthSystemModule.UserEntity) user;
        if (entity == null) return false;
        entity.serverId = serverID;
        return true;
    }

    @Override
    public void close() throws IOException {

    }
}
