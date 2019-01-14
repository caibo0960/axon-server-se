package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.KeepNames;
import io.axoniq.axonserver.applicationevents.UserEvents;
import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.platform.role.Role;
import io.axoniq.platform.role.RoleController;
import io.axoniq.platform.user.User;
import io.axoniq.platform.user.UserController;
import io.axoniq.platform.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;

/**
 * Author: marc
 */
@RestController("UserRestController")
@CrossOrigin
@RequestMapping("/v1")
public class UserRestController {

    private final Logger logger = LoggerFactory.getLogger(UserRestController.class);
    private final UserController userController;
    private final RoleController roleController;
    private final ApplicationEventPublisher eventPublisher;

    public UserRestController(UserController userController,
                              RoleController roleController,
                              ApplicationEventPublisher eventPublisher) {
        this.userController = userController;
        this.roleController = roleController;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("users")
    public void createUser(@RequestBody @Valid UserJson userJson) {
        Set<String> validRoles = roleController.listUserRoles().stream().map(Role::name).collect(Collectors.toSet());
        if( userJson.roles != null) {
            for (String role : userJson.roles) {
                if( ! validRoles.contains(role)) throw new MessagingPlatformException(ErrorCode.UNKNOWN_ROLE, role + ": Role unknown");
            }
        }
        User updatedUser = userController.updateUser(userJson.userName, userJson.password, userJson.roles);
        eventPublisher.publishEvent(new UserEvents.UserUpdated(updatedUser));
    }

    @GetMapping("public/users")
    public List<UserJson> listUsers() {
        try {
            return userController.getUsers().stream().map(UserJson::new).sorted(Comparator.comparing(UserJson::getUserName)).collect(Collectors.toList());
        } catch (Exception exception) {
            logger.info("List users failed - {}", exception.getMessage(), exception);
            throw new MessagingPlatformException(ErrorCode.OTHER, exception.getMessage());
        }
    }

    @DeleteMapping(path = "users/{name}")
    public void dropUser(@PathVariable("name") String name) {
        try {
            userController.deleteUser(name);
            eventPublisher.publishEvent(new UserEvents.UserDeleted(name));
        } catch (Exception exception) {
            logger.info("Delete user {} failed - {}", name, exception.getMessage());
            throw new MessagingPlatformException(ErrorCode.OTHER, exception.getMessage());
        }
    }

    @KeepNames
    public static class UserJson {

        private String userName;
        private String password;
        private String[] roles;

        public UserJson() {
        }

        public UserJson(User u) {
            userName = u.getUserName();
            if (u.getRoles() != null) {
                roles = u.getRoles().stream().map(UserRole::getRole).toArray(String[]::new);
            }
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String[] getRoles() {
            return roles;
        }

        public void setRoles(String[] roles) {
            this.roles = roles;
        }
    }
}
