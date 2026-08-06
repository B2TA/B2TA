## Workshop FAQ

<details>
<summary><strong>When will the Kiro workshop be held?</strong>
</summary>

Tuesday, August 4, 2026.

The workshop will be held **in person** from **1:00 PM to 5:00 PM**. Registration begins at **12:30 PM**, and a light lunch and refreshments will be provided.

</details>

<details>
<summary><strong>How will I get access?</strong></summary>

Access details will be shared during the introduction at the start of the workshop and posted in the FAQ file.

Please ensure you are using your **UBC student email** for any materials provided during the workshop.

</details>
<details>
<summary><strong>Will I be with my team for the workshop?</strong></summary>

No. This is an **individual workshop** to ensure that everyone gains direct experience working through the same challenge.

</details>

<details>
<summary><strong>How much time will I have?</strong></summary>

You will have **4 hours of in-person support** to build the application, beginning at **1:00 PM on Tuesday, August 4, 2026**.

</details>

<details>
<summary><strong>What can I expect?</strong></summary>

You will learn about the **spec-driven development process using Kiro** and apply those concepts while building an application during the workshop.

</details>

<details>
<summary><strong>What do I need to install before the Hackathon?</strong></summary>

1. **AWS CLI**
Installation Guide: https://aws.amazon.com/cli/
*Just install it. No need to create an account or configure credentials yet*

2. **Git**
Installation Guide: https://git-scm.com/install/

3. **Kiro**
Installation Guide: https://kiro.dev/docs/getting-started/installation/
*Install only. Do not log in yet.*

4. **Kiro Account**
Authentication Guide: https://kiro.dev/docs/getting-started/authentication/
*Sign up using either Social Login or AWS Builder ID.*

</details>

<details>
<summary><strong>Working During the Hackathon</strong></summary>

<details>
<summary><strong>How can I configure my AWS credentials in my terminal?</strong></summary>

In the left sidebar of AWS Workshop Studio, click "Get AWS CLI credentials" and copy the provided commands into your terminal. These will typically set three environment variables:

```bash
export AWS_ACCESS_KEY_ID=<your-access-key>
export AWS_SECRET_ACCESS_KEY=<your-secret-key>
export AWS_SESSION_TOKEN=<your-session-token>
export AWS_DEFAULT_REGION=us-east-1
```

On Windows (PowerShell):

```powershell
$env:AWS_ACCESS_KEY_ID="<your-access-key>"
$env:AWS_SECRET_ACCESS_KEY="<your-secret-key>"
$env:AWS_SESSION_TOKEN="<your-session-token>"
$env:AWS_DEFAULT_REGION="us-east-1"
```

Verify it worked with:

```bash
aws sts get-caller-identity
```

**Note:** If you already have a personal AWS account configured, no need to worry — environment variables take precedence over your `~/.aws/credentials` file, so your personal setup won't be affected. When you're done with the workshop, clear the variables to restore your personal profile:

```bash
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_DEFAULT_REGION
```

</details>

<details>
<summary><strong>What region should I use?</strong></summary>

Use **us-east-1**. Your workshop account is configured for this region, which has access to the Amazon Bedrock foundation models you'll need.

</details>

<details>
<summary><strong>How can I debug or see what's going on with the resources I created?</strong></summary>

You can use **AWS CloudWatch** to check all the logs that come from different AWS services. When you're stuck, going to CloudWatch and inspecting the logs helps you a lot.

</details>

<details>
<summary><strong>How do I redeem my Kiro credits?</strong></summary>

Go to the redemption URL provided by your facilitator: `https://kiro.dev/redeem/<code>`. Credits will be applied to whichever Kiro account you're logged into at the time of redemption — make sure you log in with the same account/method you plan to use throughout the hackathon, since that's the account the credits will be tied to.

Keep an eye on your code's expiration date (provided by your facilitator), and note that once redeemed, credits also have their own expiration date.

</details>

</details>

## Common Mistakes

<details>
<summary><strong>Extra Spaces</strong></summary>

Having an extra space in AWS command parameters can lead to unexpected issues in your AWS services, which may be difficult/time-consuming to debug. AWS commands rely on having precise syntax, so even just one extra space can cause misinterpretation. To avoid headaches, always double check all the values and properties that you pass into AWS commands and methods. This way, you can eliminate the time you spend debugging the issues caused by it.

</details>

<details>
<summary><strong>My AWS CLI credentials stopped working mid-session — what happened?</strong></summary>

This is almost always one of the following:

- **InvalidClientTokenId:** Your access key is incorrect — re-check the value.
- **SignatureDoesNotMatch:** Your secret key is incorrect — re-paste it carefully.
- **ExpiredToken:** Your session token has expired — ask your facilitator for fresh credentials.
- **Unable to locate credentials:** Your environment variables aren't set in the current terminal session — make sure you run the `export`/`$env:` commands in the same terminal window you're using.

Workshop credentials are temporary (access keys starting with `ASIA`) and require all three values (access key, secret key, session token) to be set together — `aws configure` alone won't work for these since it doesn't prompt for a session token.

</details>
