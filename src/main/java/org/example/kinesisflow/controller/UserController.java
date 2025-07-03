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


    @PostMapping("/addNewUser")
    @ResponseStatus(HttpStatus.CREATED)
    public String addNewUser(@RequestBody @Valid UserDTO userDTO) {
        User user = UserMapper.toEntity(userDTO);
        return this.userService.addUser(user);
    }

    @PostMapping("/authenticate")
    public ResponseEntity<String> authenticateAndGetToken(@RequestBody @Valid UserDTO userDTO) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userDTO.getUsername(), userDTO.getPassword())
            );
            String token = jwtService.generateToken(authentication.getName());
            return ResponseEntity.ok(token);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
        }
}}