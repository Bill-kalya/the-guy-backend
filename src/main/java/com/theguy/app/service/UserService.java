package com.theguy.app.service;

import com.theguy.app.dto.UpdateProfileRequest;
import com.theguy.app.dto.UserDto;

import java.util.UUID;

public interface UserService {
    UserDto getProfile(String email);
    UserDto updateProfile(String email, UpdateProfileRequest request);
    UserDto getUser(UUID id);
}