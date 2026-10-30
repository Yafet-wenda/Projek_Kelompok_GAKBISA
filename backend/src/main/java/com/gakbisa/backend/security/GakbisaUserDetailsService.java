package com.gakbisa.backend.security;

import com.gakbisa.backend.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GakbisaUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public GakbisaUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email.toLowerCase().trim())
            .map(GakbisaUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("Akun tidak ditemukan"));
    }
}
