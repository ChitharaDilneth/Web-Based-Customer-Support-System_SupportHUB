package com.customersupport.SupportHUB.customer;

import com.customersupport.SupportHUB.common.AccessDeniedException;
import com.customersupport.SupportHUB.common.ApiResponse;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATIONS_SUPERVISOR', 'CUSTOMER_SUPPORT_MANAGER', 'CUSTOMER_SERVICE_OFFICER')")
    public ResponseEntity<ApiResponse<CustomerDto>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDto created = customerService.createCustomer(request);
        return ResponseEntity.ok(ApiResponse.success("Customer account created successfully", created));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerDto>> getMyProfile(Authentication authentication) {
        CustomerDto customer = customerService.getCustomerByEmail(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Customer profile fetched successfully", customer));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS_SUPERVISOR', 'CUSTOMER_SUPPORT_MANAGER', 'QA_EXECUTIVE', 'CUSTOMER_SERVICE_OFFICER')")
    public ResponseEntity<ApiResponse<CustomerDto>> getCustomerById(@PathVariable("id") Long id) {
        CustomerDto customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", customer));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS_SUPERVISOR', 'CUSTOMER_SUPPORT_MANAGER', 'QA_EXECUTIVE', 'CUSTOMER_SERVICE_OFFICER')")
    public ResponseEntity<ApiResponse<List<CustomerDto>>> getAllCustomers() {
        return ResponseEntity.ok(ApiResponse.success("Customers list fetched successfully", customerService.getAllCustomers()));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('OPERATIONS_SUPERVISOR', 'CUSTOMER_SUPPORT_MANAGER', 'QA_EXECUTIVE', 'CUSTOMER_SERVICE_OFFICER')")
    public ResponseEntity<ApiResponse<Page<CustomerDto>>> searchCustomers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by("id").descending());
        return ResponseEntity.ok(ApiResponse.success("Customer search completed", customerService.searchCustomers(keyword, pageRequest)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CustomerDto>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateCustomerRequest request) {
        CustomerDto current = customerService.getCustomerByEmail(authentication.getName());
        CustomerDto updated = customerService.updateCustomer(current.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Customer profile updated successfully", updated));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CustomerDto>> updateCustomer(
            @PathVariable("id") Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateCustomerRequest request) {
        if (authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
            CustomerDto current = customerService.getCustomerByEmail(authentication.getName());
            if (!current.getId().equals(id)) {
                throw new AccessDeniedException("You are not authorized to update another customer's profile");
            }
        }
        CustomerDto updated = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(ApiResponse.success("Customer profile updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OPERATIONS_SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable("id") Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully"));
    }

    @RequestMapping(value = "/check-inactivity", method = { RequestMethod.POST, RequestMethod.GET })
    @PreAuthorize("hasAnyRole('OPERATIONS_SUPERVISOR', 'CUSTOMER_SUPPORT_MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkCustomerInactivity(
            @RequestParam(value = "days", defaultValue = "180") int days) {
        int deactivated = customerService.deactivateInactiveCustomers(days);
        return ResponseEntity.ok(ApiResponse.success(
                "Customer inactivity check completed. Deactivated " + deactivated + " inactive account(s).",
                Map.of("deactivatedCount", deactivated, "thresholdDays", days)));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('OPERATIONS_SUPERVISOR', 'CUSTOMER_SUPPORT_MANAGER')")
    public ResponseEntity<ApiResponse<CustomerDto>> reactivateCustomer(
            @PathVariable("id") Long id,
            Authentication authentication) {
        CustomerDto reactivated = customerService.reactivateCustomer(id,
                authentication != null ? authentication.getName() : null);
        return ResponseEntity.ok(ApiResponse.success("Customer account reactivated successfully", reactivated));
    }
}
