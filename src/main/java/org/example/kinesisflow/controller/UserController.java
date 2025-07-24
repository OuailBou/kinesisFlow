package org.example.kinesisflow.controller;
import jakarta.validation.Valid;
import org.example.kinesisflow.dto.UserDTO;
import org.example.kinesisflow.mapper.UserMapper;
import org.example.kinesisflow.model.User;
import org.example.kinesisflow.service.JwtService;
import org.example.kinesisflow.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public UserController(UserService userService, JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }


    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> addNewUser(@RequestBody @Valid UserDTO userDTO) {
        User user = UserMapper.toEntity(userDTO);
        userService.addUser(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User created");
        response.put("username", user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> authenticateAndGetToken(@RequestBody @Valid UserDTO userDTO) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userDTO.getUsername(), userDTO.getPassword())
        );
        String token = jwtService.generateToken(authentication.getName());

        Map<String, String> response = new HashMap<>();
        response.put("token", token);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/h")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
    @GetMapping("/CDOK")
    public ResponseEntity<String> testCD() {
        return ResponseEntity.ok("OK");
    }

}