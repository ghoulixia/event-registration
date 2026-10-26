package com.team.eventregistration.repository;

import com.team.eventregistration.entity.User;
import com.team.eventregistration.service.port.UserStore;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class UserStoreAdapter implements UserStore {
    private final UserRepository users;
    public UserStoreAdapter(UserRepository users) { this.users = users; }
    public Optional<User> find(Long id) { return users.findById(id); }
}
