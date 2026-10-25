package com.team.eventregistration.service.port;

import com.team.eventregistration.entity.User;
import java.util.Optional;

public interface UserStore {
    Optional<User> find(Long id);
}
