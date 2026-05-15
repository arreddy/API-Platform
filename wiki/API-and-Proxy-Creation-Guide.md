# Quick Guide: Creating APIs and Proxies

This guide walks you through the process of creating and managing APIs and proxies in the API-Platform ecosystem.

## Table of Contents
1. [Creating an API](#creating-an-api)
2. [Setting Up a Proxy](#setting-up-a-proxy)
3. [Configuration Best Practices](#configuration-best-practices)
4. [Testing and Validation](#testing-and-validation)
5. [Monitoring and Analytics](#monitoring-and-analytics)

---

## Creating an API

### Prerequisites
- API-Platform instance running
- OpenAPI Specification (OAS) 3.0 or later (recommended)
- Backend service endpoint(s)
- API credentials/keys for authentication

### Step 1: Register Your API

1. **Navigate to API Management Console**
   - Access the API-Platform dashboard
   - Click on "APIs" → "Register New API"

2. **Upload OpenAPI Specification**
   - Click "Upload OAS" and select your OpenAPI spec file
   - Alternatively, paste your OAS JSON/YAML directly
   - Validate the specification

3. **Configure Basic Settings**
   ```
   API Name:        MyAPI
   Version:         1.0.0
   Description:     Brief description of your API
   Base Path:       /api/v1
   ```

4. **Define Backend Target**
   - **Target Host**: `https://backend.example.com`
   - **Target Path**: `/api` (optional path prefix on backend)
   - **Protocol**: HTTP/HTTPS
   - **Timeout**: 30000ms (configurable)

### Step 2: Configure Authentication

Choose your authentication method:

**API Key Authentication**
```
Type:           API Key
Location:       Header
Header Name:    X-API-Key
Key Length:     32 characters (recommended)
```

**OAuth 2.0**
```
Authorization Server:    https://auth.example.com
Token Endpoint:          /oauth/token
Scopes:                  read:api, write:api
```

**Basic Auth**
```
Type:      HTTP Basic
Format:    base64(username:password)
```

### Step 3: Define API Resources and Methods

For each resource in your API:

1. Click "Add Resource"
2. Configure the following:
   ```
   Resource Path:    /users
   Description:      User management endpoints
   
   Methods:
   - GET    /users          → List all users
   - POST   /users          → Create new user
   - GET    /users/{id}     → Get user by ID
   - PUT    /users/{id}     → Update user
   - DELETE /users/{id}     → Delete user
   ```

### Step 4: Apply Rate Limiting (Optional)

```
Rate Limit Type:      Per-IP / Per-API-Key
Requests Per Minute:  100
Burst Size:           10
```

### Step 5: Publish Your API

1. Review all settings
2. Click "Publish API"
3. Your API is now live and accessible

---

## Setting Up a Proxy

### What is a Proxy?

A proxy acts as an intermediary between clients and your backend services. It enables:
- Request/response transformation
- Traffic routing
- Security enforcement
- Rate limiting and throttling
- Caching

### Step 1: Create a New Proxy

1. **Navigate to Proxies Section**
   - Dashboard → "Proxies" → "Create New Proxy"

2. **Basic Configuration**
   ```
   Proxy Name:        MyAPIProxy
   Base Path:         /proxy/myapi
   Target Backend:    https://backend.example.com
   ```

### Step 2: Configure Request Processing

**Define Request Policies:**

```yaml
Request Policies:
  - Policy: ConvertToJSON
    Description: Ensure all requests are JSON format
    
  - Policy: ValidateSchema
    Schema: Request Schema
    
  - Policy: AuthenticationCheck
    Type: API Key
    
  - Policy: RateLimiting
    Requests/Minute: 100
    
  - Policy: HeaderTransformation
    Add Headers:
      X-Request-ID: "{uuid()}"
      X-Client-IP: "{client_ip}"
```

### Step 3: Configure Response Processing

**Define Response Policies:**

```yaml
Response Policies:
  - Policy: HeaderTransformation
    Add Headers:
      X-RateLimit-Remaining: "{rate_limit_remaining}"
      Cache-Control: "public, max-age=300"
    Remove Headers:
      - X-Internal-Server
      - X-Debug-Info
      
  - Policy: ConvertToJSON
    Target Format: application/json
    
  - Policy: ResponseCaching
    TTL: 300 seconds
```

### Step 4: Set Up Target Endpoints

**Primary Endpoint:**
```
Host:        backend.example.com
Port:        443
Protocol:    HTTPS
Path Prefix: /api/v1
Load Balance: Round Robin
```

**Fallback Endpoint (Optional):**
```
Host:        backup.example.com
Port:        443
Protocol:    HTTPS
Condition:   When primary is unavailable
```

### Step 5: Enable Caching (Optional)

```
Cache Settings:
  Enabled:           true
  TTL (seconds):     300
  Cache Key:         {method}:{path}:{api-key}
  Cacheable Methods: GET
```

### Step 6: Deploy Proxy

1. Review all configurations
2. Click "Deploy Proxy"
3. Select deployment environment (Development/Staging/Production)
4. Confirm deployment

---

## Configuration Best Practices

### Security
- ✅ Always use HTTPS for backend endpoints
- ✅ Rotate API keys regularly (every 90 days)
- ✅ Use OAuth 2.0 for sensitive APIs
- ✅ Implement IP whitelisting when possible
- ✅ Never expose sensitive headers in responses

### Performance
- ✅ Enable response caching for GET requests
- ✅ Set appropriate timeout values (30-60 seconds)
- ✅ Use gzip compression for responses
- ✅ Implement rate limiting to prevent abuse
- ✅ Monitor backend response times

### Reliability
- ✅ Configure health check endpoints
- ✅ Set up fallback/backup endpoints
- ✅ Implement automatic retry logic
- ✅ Use circuit breaker pattern for resilience
- ✅ Log all errors and anomalies

### Documentation
- ✅ Keep OpenAPI specs up-to-date
- ✅ Document all custom headers
- ✅ Provide example requests and responses
- ✅ Include error code definitions
- ✅ Document rate limits and quotas

---

## Testing and Validation

### Pre-Deployment Testing

**1. Schema Validation**
```bash
# Validate OpenAPI specification
POST /api/validate
Content-Type: application/json

{
  "spec": { /* OAS JSON */ }
}
```

**2. Endpoint Testing**
```bash
# Test API endpoint
curl -X GET https://api-platform.example.com/api/v1/users \
  -H "X-API-Key: your-api-key"
```

**3. Load Testing**
```bash
# Simulate load with 1000 requests
ab -n 1000 -c 100 \
  -H "X-API-Key: your-api-key" \
  https://api-platform.example.com/api/v1/users
```

### Post-Deployment Monitoring

**Key Metrics to Monitor:**
- Response time (p50, p95, p99)
- Error rate (5xx, 4xx)
- Rate limit violations
- Cache hit ratio
- Backend availability

---

## Monitoring and Analytics

### Access Analytics Dashboard

1. Dashboard → "Analytics"
2. Select timeframe (last hour, day, week, month)
3. View metrics:
   - **Traffic**: Requests per minute
   - **Errors**: Error rate and distribution
   - **Latency**: Response time metrics
   - **Top Resources**: Most accessed endpoints
   - **Top Consumers**: API key usage

### Setting Up Alerts

```
Alert Rule: High Error Rate
Condition:   Error rate > 5%
Duration:    5 minutes
Action:      Send email to team@example.com

Alert Rule: Slow Response Time
Condition:   p95 latency > 2000ms
Duration:    10 minutes
Action:      Page on-call engineer
```

### Developer Portal Features

- **API Discovery**: Browse and search APIs
- **Self-Service Key Generation**: Create API keys
- **Usage Analytics**: Track your consumption
- **Documentation**: Auto-generated from OpenAPI specs
- **Interactive Testing**: Built-in API tester

---

## Common Issues and Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| 502 Bad Gateway | Backend unavailable | Check backend health; verify endpoint URL |
| 401 Unauthorized | Invalid/missing API key | Generate new API key; verify header name |
| 429 Too Many Requests | Rate limit exceeded | Wait before retrying; request higher quota |
| Slow responses | Backend latency | Check backend performance; enable caching |
| CORS errors | Missing CORS headers | Enable CORS in proxy settings |

---

## Next Steps

1. Review the [Setup and Installation Guide](./Setup-and-Installation.md) for environment setup
2. Check the [API Management](./API-Management.md) page for advanced features
3. Explore [Security Best Practices](./Security-Best-Practices.md)
4. Join our [Developer Community](https://community.example.com)

---

**Need Help?**
- 📧 Email: support@api-platform.example.com
- 💬 Community: [GitHub Discussions](https://github.com/arreddy/API-Platform/discussions)
- 📚 Full Documentation: [API-Platform Docs](https://docs.example.com)
