package org.example.kinesisflow.service;

import org.example.kinesisflow.exception.UserAlreadyExistsException;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository repository;
    private final PasswordEncoder encoder;

    @Autowired
    public UserService(UserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = repository.findByUsername(username);

        if (user.isEmpty()) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        // Convert User to UserDetails (UserInfoDetails)
        User userInfo = user.get();
        return new UserInfoDetails(userInfo);
    }
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username);
    }


    public String addUser(User user) {
        if (repository.existsByUsername(user.getUsername())) {
            throw new UserAlreadyExistsException("User " + user.getUsername() + " already exists");

        }

        user.setPassword(encoder.encode(user.getPassword()));

        repository.save(user);

        return "User added successfully!";
    }

}