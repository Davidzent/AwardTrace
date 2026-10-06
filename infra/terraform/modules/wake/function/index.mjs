// The landing page's wake button (ADR 0020), as a Lambda function behind a function URL. wake.mjs decides what to do;
// this file supplies the AWS calls. The AWS SDK comes with the Lambda runtime, so there is nothing to install.
import { ConditionalCheckFailedException, DynamoDBClient, UpdateItemCommand } from '@aws-sdk/client-dynamodb';
import { DescribeInstancesCommand, EC2Client, StartInstancesCommand } from '@aws-sdk/client-ec2';
import { route } from './wake.mjs';

const { INSTANCE_ID, TABLE_NAME, APP_URL, WAKES_PER_DAY } = process.env;
const ec2 = new EC2Client({});
const dynamodb = new DynamoDBClient({});

export const handler = route({
  async instanceState() {
    const { Reservations } = await ec2.send(new DescribeInstancesCommand({ InstanceIds: [INSTANCE_ID] }));
    return Reservations?.[0]?.Instances?.[0]?.State?.Name;
  },

  // Caddy passes /healthz to the app's health check. It's outside /api/, so asking can't keep the host awake.
  async appReady() {
    try {
      return (await fetch(`${APP_URL}/healthz`, { signal: AbortSignal.timeout(3000) })).ok;
    } catch {
      return false;
    }
  },

  // One item per UTC day. The condition makes the count atomic, so two wakes at once can't both take the last one.
  async takeWake(day) {
    try {
      await dynamodb.send(
        new UpdateItemCommand({
          TableName: TABLE_NAME,
          Key: { day: { S: day } },
          UpdateExpression: 'SET expires_at = :expires ADD wakes :one',
          ConditionExpression: 'attribute_not_exists(wakes) OR wakes < :max',
          ExpressionAttributeValues: {
            ':one': { N: '1' },
            ':max': { N: WAKES_PER_DAY },
            // DynamoDB deletes the item on its own a few days later.
            ':expires': { N: String(Math.floor(Date.now() / 1000) + 3 * 86_400) },
          },
        }),
      );
      return true;
    } catch (error) {
      if (error instanceof ConditionalCheckFailedException) {
        return false;
      }
      throw error;
    }
  },

  async startInstance() {
    await ec2.send(new StartInstancesCommand({ InstanceIds: [INSTANCE_ID] }));
  },
});
