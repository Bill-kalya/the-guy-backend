package com.theguy.app.service;

import com.theguy.app.dto.admin.ProviderImportResultDTO;
import com.theguy.app.entity.Category;
import com.theguy.app.entity.Provider;
import com.theguy.app.entity.ProviderLocation;
import com.theguy.app.entity.User;
import com.theguy.app.enums.Role;
import com.theguy.app.enums.VerificationLevel;
import com.theguy.app.repository.CategoryRepository;
import com.theguy.app.repository.ProviderLocationRepository;
import com.theguy.app.repository.ProviderRepository;
import com.theguy.app.repository.UserRepository;
import com.theguy.app.utils.CountyCoordinates;
import com.theguy.app.utils.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderImportService {

    private static final int BATCH_SIZE = 100;
    private static final String EMAIL_DOMAIN = "@unclaimed.theguy.local";

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProviderLocationRepository providerLocationRepository;

    @Transactional
    public ProviderImportResultDTO importProviders(MultipartFile file) {
        List<String> invalidRows = new ArrayList<>();
        int totalRows = 0;

        Set<String> seenPhones = new HashSet<>(userRepository.findAllPhoneNumbers());

        Map<String, Category> categoriesByName = loadCategoriesByName();

        List<Row> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                totalRows++;
                String name = trimOrNull(record.get("name"));
                String rawPhone = trimOrNull(record.get("phone"));
                String categoryName = trimOrNull(record.get("category"));
                String county = trimOrNull(record.get("county"));
                String rawLat = trimOrNull(record.get("lat"));
                String rawLng = trimOrNull(record.get("lng"));

                if (name == null || rawPhone == null) {
                    invalidRows.add("Row " + totalRows + ": missing required columns (name, phone)");
                    continue;
                }

                String phone = PhoneNormalizer.normalize(rawPhone);
                if (phone == null) {
                    invalidRows.add("Row " + totalRows + ": invalid phone '" + rawPhone + "'");
                    continue;
                }

                if (!seenPhones.add(phone)) {
                    invalidRows.add("Row " + totalRows + ": duplicate or already-registered phone '" + rawPhone + "'");
                    continue;
                }

                Double lat = parseDouble(rawLat);
                Double lng = parseDouble(rawLng);
                if (lat == null || lng == null) {
                    double[] coords = CountyCoordinates.find(county);
                    if (coords != null) {
                        lat = coords[0];
                        lng = coords[1];
                    }
                }
                if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                    lat = null;
                    lng = null;
                }

                rows.add(new Row(name, phone, categoryName, lat, lng));
            }
        } catch (Exception e) {
            log.error("Failed to parse provider CSV", e);
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage());
        }

        int imported = 0;
        List<Provider> savedProviders = new ArrayList<>();

        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Row> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));

            List<User> users = batch.stream().map(row -> {
                User user = new User();
                user.setFullName(row.name);
                user.setPhoneNumber(row.phone);
                user.setEmail(row.phone + EMAIL_DOMAIN);
                user.setPasswordHash(UUID.randomUUID().toString().replace("-", ""));
                user.setRole(Role.PROVIDER);
                user.setVerified(false);
                return user;
            }).collect(Collectors.toList());

            userRepository.saveAll(users);

            List<Provider> providers = new ArrayList<>();
            for (int j = 0; j < batch.size(); j++) {
                Row row = batch.get(j);
                User user = users.get(j);

                Provider provider = new Provider();
                provider.setUser(user);
                provider.setAccountClaimed(false);
                provider.setOnline(false);
                provider.setVerificationLevel(VerificationLevel.NONE);
                provider.setProviderStatus("ACTIVE");
                provider.setResponseRate(1.0);
                provider.setDynamicPriceMultiplier(1.0);
                if (row.categoryName != null) {
                    Category category = matchCategory(categoriesByName, row.categoryName);
                    if (category != null) {
                        provider.setCategoryId(category.getId().toString());
                    }
                }
                providers.add(provider);
            }

            providerRepository.saveAll(providers);
            imported += providers.size();
            savedProviders.addAll(providers);
        }

        List<ProviderLocation> locationsToSave = new ArrayList<>();
        Map<String, Row> rowsByPhone = rows.stream()
                .collect(Collectors.toMap(r -> r.phone, r -> r));
        for (Provider provider : savedProviders) {
            String phone = provider.getUser() != null ? provider.getUser().getPhoneNumber() : null;
            Row row = phone == null ? null : rowsByPhone.get(phone);
            if (row != null && row.lat != null && row.lng != null) {
                ProviderLocation location = new ProviderLocation();
                location.setProviderId(provider.getId());
                location.setLatitude(row.lat);
                location.setLongitude(row.lng);
                locationsToSave.add(location);
            }
        }
        providerLocationRepository.saveAll(locationsToSave);

        log.info("Provider import complete: total={}, imported={}, invalid={}", totalRows, imported, invalidRows.size());
        return ProviderImportResultDTO.builder()
                .totalRows(totalRows)
                .imported(imported)
                .skippedDuplicatePhone(totalRows - imported - invalidRows.size())
                .invalidRows(invalidRows)
                .build();
    }

    private Map<String, Category> loadCategoriesByName() {
        Map<String, Category> map = new LinkedHashMap<>();
        for (Category category : categoryRepository.findByIsActiveTrueOrderBySortOrderAsc()) {
            map.put(category.getName().toLowerCase(), category);
        }
        return map;
    }

    private Category matchCategory(Map<String, Category> categoriesByName, String name) {
        if (name == null) return null;
        String key = name.toLowerCase();
        if (categoriesByName.containsKey(key)) return categoriesByName.get(key);
        for (Map.Entry<String, Category> entry : categoriesByName.entrySet()) {
            if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double parseDouble(String value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Row(String name, String phone, String categoryName, Double lat, Double lng) {
    }
}
