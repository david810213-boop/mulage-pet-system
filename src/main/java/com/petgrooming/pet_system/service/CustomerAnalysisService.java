package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.CustomerAnalysisResponse;
import com.petgrooming.pet_system.enums.AppointmentStatus;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.Appointment;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.AppointmentRepository;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerAnalysisService {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public CustomerAnalysisResponse getOverview() {
        List<User> customers = userRepository.findByRole(UserRole.CUSTOMER);

        // 每位會員的「有效預約數」（排除已取消），用來判斷新客／回流客
        Map<Long, Long> validAppointmentCountByUser = appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .filter(a -> a.getUser() != null)
                .collect(Collectors.groupingBy(a -> a.getUser().getId(), Collectors.counting()));

        long newCustomers = 0;
        long returningCustomers = 0;
        long neverBooked = 0;

        for (User c : customers) {
            long count = validAppointmentCountByUser.getOrDefault(c.getId(), 0L);
            if (count == 0) neverBooked++;
            else if (count == 1) newCustomers++;
            else returningCustomers++;
        }

        long profileCompleted = customers.stream()
                .filter(c -> c.getProfileCompletedAt() != null)
                .count();

        return CustomerAnalysisResponse.builder()
                .totalCustomers(customers.size())
                .newCustomers(newCustomers)
                .returningCustomers(returningCustomers)
                .neverBookedCustomers(neverBooked)
                .profileCompletedCount(profileCompleted)
                .profileCompletionRate(customers.isEmpty() ? 0.0
                        : Math.round(profileCompleted * 1000.0 / customers.size()) / 10.0)
                .ageBreakdown(buildBreakdown(customers, this::ageGroupOf))
                .occupationBreakdown(buildBreakdown(customers,
                        c -> c.getOccupation() == null || c.getOccupation().isBlank() ? "未填寫" : c.getOccupation().trim()))
                .areaBreakdown(buildBreakdown(customers,
                        c -> c.getResidenceArea() == null || c.getResidenceArea().isBlank() ? "未填寫" : c.getResidenceArea().trim()))
                .sourceBreakdown(buildBreakdown(customers,
                        c -> c.getSource() == null ? "未填寫" : c.getSource().getLabel()))
                .build();
    }

    private String ageGroupOf(User c) {
        Integer age = c.getAge();
        if (age == null) return "未填寫";
        if (age < 20) return "20歲以下";
        if (age < 30) return "20-29歲";
        if (age < 40) return "30-39歲";
        if (age < 50) return "40-49歲";
        if (age < 60) return "50-59歲";
        return "60歲以上";
    }

    // 依指定分類函式統計人數，並依人數由多到少排序（「未填寫」固定排最後）
    private Map<String, Long> buildBreakdown(List<User> customers, Function<User, String> classifier) {
        Map<String, Long> counted = customers.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));

        return counted.entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey().equals("未填寫")) return 1;
                    if (b.getKey().equals("未填寫")) return -1;
                    return Long.compare(b.getValue(), a.getValue());
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> x, LinkedHashMap::new));
    }
}
